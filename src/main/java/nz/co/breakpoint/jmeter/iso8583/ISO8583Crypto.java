package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.jpos.emv.EMVStandardTagType;
import static org.jpos.emv.EMVStandardTagType.*;
import org.jpos.emv.UnknownTagNumberException;
import org.jpos.iso.*;
import org.jpos.security.MKDMethod;
import org.jpos.security.SKDMethod;
import org.jpos.security.jceadapter.JCEHandlerException;
import org.jpos.tlv.ISOTaggedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ISO8583Crypto extends AbstractTestElement
        implements ISO8583TestElement, PreProcessor, TestBean, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ISO8583Crypto.class);

    public static final String
        MACALGORITHM = "macAlgorithm",
        MACKEY = "macKey",
        PINFIELD = "pinField",
        PINKEY = "pinKey",
        ARQCFIELD = "arqcField",
        IMKAC = "imkac",
        SKDM = "skdm";

    static final String[] macAlgorithms = new String[]{"", "DESEDE", "ISO9797ALG3MACWITHISO7816-4PADDING"};
    static final String[] skdMethods;
    static {
        SKDMethod[] all = SKDMethod.values();
        skdMethods = new String[all.length];
        for (int i=0; i<all.length; ++i) skdMethods[i] = all[i].name();
    }

    // TODO configurable via JMeter property
    //  "jmeter.iso8583.arqcInputTags"="9F02,9F03,9F1A,95,5F2A,9A,9C,9F37,82,9F36,9F10"
    static final EMVStandardTagType[] minimumArqcInputs = new EMVStandardTagType[]{
        AMOUNT_AUTHORISED_NUMERIC_0x9F02,
        AMOUNT_OTHER_NUMERIC_0x9F03,
        TERMINAL_COUNTRY_CODE_0x9F1A,
        TERMINAL_VERIFICATION_RESULTS_0x95,
        TRANSACTION_CURRENCY_CODE_0x5F2A,
        TRANSACTION_DATE_0x9A,
        TRANSACTION_TYPE_0x9C,
        UNPREDICTABLE_NUMBER_0x9F37,
        APPLICATION_INTERCHANGE_PROFILE_0x82,
        APPLICATION_TRANSACTION_COUNTER_0x9F36,
        ISSUER_APPLICATION_DATA_0x9F10
    };

    protected transient SecurityModule securityModule = new SecurityModule();
    protected transient Key macKey, pinKey;

    @Override
    public void process() {
        JMeterContext context = getThreadContext();
        Sampler current = context.getCurrentSampler();
        if (!(current instanceof ISO8583Sampler)) return;

        log.debug("Processing sampler '{}'", current.getName());
        ISO8583Sampler sampler = (ISO8583Sampler)current;

        if (securityModule == null) {
            log.warn("SecurityModule undefined"); // should have logged error earlier
            return;
        }
        encryptPINBlock(sampler);
        calculateARQC(sampler);
        calculateMAC(sampler);
    }

    protected void calculateMAC(ISO8583Sampler sampler) {
        final String macKeyHex = getMacKey(), macAlgorithm = getMacAlgorithm();

        if (macAlgorithm == null || macAlgorithm.isEmpty()) {
            log.debug("No MAC algorithm defined, skipping MAC calculation");
            return;
        }
        if (macKeyHex == null || macKeyHex.isEmpty()) {
            log.debug("No MAC key defined, skipping MAC calculation");
            return;
        }
        if (macKeyHex.length() != 32 && macKeyHex.length() != 48) {
            log.error("Incorrect MAC key length '{}' (expecting 32 or 48 hex digits)", macKeyHex);
            return;
        }
        Key newKey = new SecretKeySpec(ISOUtil.hex2byte(macKeyHex), macAlgorithm);
        if (!newKey.equals(this.macKey)) {
            /* Only assign new key if it is different, to prevent leaking MacEngineKeys in JCEHandler
             * that only compare keys by reference:
             * https://github.com/jpos/jPOS/blob/v2_1_3/jpos/src/main/java/org/jpos/security/jceadapter/JCEHandler.java#L442
             */
            log.debug("New MAC key instance assigned");
            this.macKey = newKey;
        }
        ISOMsg msg = sampler.getRequest();
        if (msg.getPackager() == null) {
            log.error("Packager undefined, skipping MAC calculation");
            return;
        }
        int macField = ((msg.getMaxField() - 1)/MAC_FIELD_NO + 1)*MAC_FIELD_NO; // round up to the next multiple of 64
        int macLength = ((ISOBasePackager)msg.getPackager()).getFieldPackager(macField).getLength();
        final String dummyMac = String.format("%0" + 2*macLength + "d", 0);
        msg.set(macField, dummyMac);
        try {
            byte[] packedMsg = msg.pack();
            // cut off MAC bytes and don't include them in calculation:
            String mac = securityModule.generateMAC(Arrays.copyOf(packedMsg, packedMsg.length-macLength),
                macKey, macAlgorithm);
            sampler.addField(String.valueOf(macField), ISOUtil.padright(mac, 2*macLength, 'f'));
        } catch (ISOException e) {
            log.error("MAC calculation failed {}", e.toString(), e);
        }
    }

    protected void encryptPINBlock(ISO8583Sampler sampler) {
        final String pinKeyHex = getPinKey(), pinField = getPinField();

        if (pinField == null || pinField.isEmpty()) {
            log.debug("No PIN field defined, skipping PIN Block encryption");
            return;
        }
        if (pinKeyHex == null || pinKeyHex.isEmpty()) {
            log.debug("No PIN key defined, skipping PIN Block encryption");
            return;
        }
        switch (pinKeyHex.length()) {
            case 16:
                pinKey = new SecretKeySpec(ISOUtil.hex2byte(pinKeyHex), "DES");
                break;
            case 32:
            case 48:
                pinKey = new SecretKeySpec(ISOUtil.hex2byte(pinKeyHex), "DESede");
                break;
            default:
                log.error("Incorrect PIN key length '{}' (expecting 16, 32 or 48 hex digits)", pinKeyHex);
                return;
        }
        ISOMsg msg = sampler.getRequest();
        if (!msg.hasField(pinField) || msg.getString(pinField).isEmpty()) {
            log.debug("No PIN Block defined, skipping PIN Block encryption");
            return;
        }
        try {
            sampler.addField(pinField, securityModule.encryptPINBlock(msg.getBytes(pinField), pinKey));
        } catch (JCEHandlerException e) {
            log.error("PIN Block encryption failed {}", e.toString(), e);
        }
    }

    protected void calculateARQC(ISO8583Sampler sampler) {
        final String hexKey = getImkac(), fieldNo = getArqcField(), skdm = getSkdm();

        if (fieldNo == null || fieldNo.isEmpty()) {
            log.debug("No ARQC field defined, skipping ARQC calculation");
            return;
        }
        if (hexKey == null || hexKey.isEmpty()) {
            log.debug("No IMKAC defined, skipping ARQC calculation");
            return;
        }
        if (skdm == null || skdm.isEmpty()) {
            log.debug("No SKDM defined, skipping ARQC calculation");
            return;
        }
        if (hexKey.length() != 32) {
            log.error("Incorrect IMKAC length '{}' (expecting 32 hex digits)", hexKey);
            return;
        }
        ISOMsg msg = sampler.getRequest();
        Map<EMVStandardTagType, String> emvData = new HashMap<>();
        try {
            // TODO maybe specify parent field as input, and additional inputData separately?
            String parentField = fieldNo.replaceAll("(.*)\\.[0-9]*", "$1");
            ISOComponent parent = msg.getComponent(parentField);
            if (parent != null) {
                for (Object c : parent.getChildren().values()) {
                    if (c instanceof ISOTaggedField) {
                        ISOTaggedField f = (ISOTaggedField) c;
                        if (f.getBytes() != null && f.getBytes().length > 0) {
                            try {
                                emvData.put(EMVStandardTagType.forHexCode(f.getTag()), ISOUtil.byte2hex(f.getBytes()));
                            } catch (UnknownTagNumberException e) {
                                log.warn("Unknown tag found in ARQC input fields {}", f.getTag(), e.toString());
                            }
                        }
                    }
                }
            }
        } catch (ISOException e) {
            log.error("ARQC calculation failed {}", e.toString(), e);
            return;
        }
        StringBuffer transactionData = new StringBuffer();
        for (EMVStandardTagType tag : minimumArqcInputs) {
            transactionData.append(emvData.getOrDefault(tag, ""));
        }
        // Append any additional data already present in the input field (as hex string):
        transactionData.append(msg.getString(fieldNo));

        String arqc = securityModule.calculateARQC(MKDMethod.OPTION_A,
            SKDMethod.valueOf(getSkdm()), hexKey,
            emvData.getOrDefault(APPLICATION_PRIMARY_ACCOUNT_NUMBER_0x5A, ""),
            emvData.getOrDefault(APPLICATION_PRIMARY_ACCOUNT_NUMBER_SEQUENCE_NUMBER_0x5F34, ""),
            emvData.getOrDefault(APPLICATION_TRANSACTION_COUNTER_0x9F36, ""),
            emvData.getOrDefault(UNPREDICTABLE_NUMBER_0x9F37, ""),
            transactionData.toString());

        sampler.addField(fieldNo, arqc, APPLICATION_CRYPTOGRAM_0x9F26.getTagNumberHex());
    }

    public String getMacAlgorithm() { return getPropertyAsString(MACALGORITHM); }
    public void setMacAlgorithm(String macAlgorithm) { setProperty(MACALGORITHM, macAlgorithm); }

    public String getMacKey() { return getPropertyAsString(MACKEY); }
    public void setMacKey(String macKey) { setProperty(MACKEY, macKey); }

    public String getPinKey() { return getPropertyAsString(PINKEY); }
    public void setPinKey(String pinKey) { setProperty(PINKEY, pinKey); }

    public String getPinField() { return getPropertyAsString(PINFIELD); }
    public void setPinField(String pinField) { setProperty(PINFIELD, pinField); }

    public String getArqcField() { return getPropertyAsString(ARQCFIELD); }
    public void setArqcField(String arqcField) { setProperty(ARQCFIELD, arqcField); }

    public String getImkac() { return getPropertyAsString(IMKAC); }
    public void setImkac(String imkac) { setProperty(IMKAC, imkac); }

    public String getSkdm() { return getPropertyAsString(SKDM); }
    public void setSkdm(String skdm) { setProperty(SKDM, skdm); }

}
