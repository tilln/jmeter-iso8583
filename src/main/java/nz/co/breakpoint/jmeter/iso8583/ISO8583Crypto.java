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
import org.apache.jmeter.util.JMeterUtils;
import static org.jpos.emv.EMVStandardTagType.*;
import org.jpos.iso.*;
import org.jpos.security.MKDMethod;
import org.jpos.security.SKDMethod;
import org.jpos.security.jceadapter.JCEHandlerException;
import org.jpos.tlv.ISOTaggedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Preprocessor for cryptographic calculations on the ISO8583Sampler's message fields.
 * Implemented are PIN Block encryption (DUKPT or zone encryption), MAC and ARQC generation.
 */
public class ISO8583Crypto extends AbstractTestElement
        implements ISO8583TestElement, PreProcessor, TestBean, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ISO8583Crypto.class);

    public static final String
        MACALGORITHM = "macAlgorithm",
        MACKEY = "macKey",
        MACFIELD = "macField",
        PINFIELD = "pinField",
        PINKEY = "pinKey",
        KSNFIELD = "ksnField",
        ICCFIELD = "iccField",
        IMKAC = "imkac",
        SKDM = "skdm",
        PAN = "pan",
        PSN = "psn",
        TXNDATA = "txnData";

    static final String[] macAlgorithms = new String[]{"", "DESEDE", "ISO9797ALG3MACWITHISO7816-4PADDING"};
    static final String[] skdMethods;
    static {
        SKDMethod[] all = SKDMethod.values();
        skdMethods = new String[all.length];
        for (int i=0; i<all.length; ++i) skdMethods[i] = all[i].name();
    }

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
        final String macKeyHex = getMacKey(), macAlgorithm = getMacAlgorithm(), fixedMacField = getMacField();

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
        int macField = (fixedMacField != null && !fixedMacField.isEmpty()) ? Integer.parseInt(fixedMacField) :
            ((msg.getMaxField() - 1)/MAC_FIELD_NO + 1)*MAC_FIELD_NO; // round up to the next multiple of 64

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
        final String pinKeyHex = getPinKey(), pinField = getPinField(), ksnField = getKsnField();

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
        String encryptedPINBlock;
        if (ksnField != null && !ksnField.isEmpty()) {
            if (!msg.hasField(ksnField)) {
                log.debug("No KSN defined, skipping PIN Block encryption");
                return;
            }
            if (pinKeyHex.length() == 16) {
                log.error("Incorrect BDK length '{}' (expecting 32 or 48 hex digits)", pinKeyHex);
                return;
            }
            log.debug("KSN found, doing DUKPT encryption");
            encryptedPINBlock = securityModule.encryptPINBlock(msg.getBytes(pinField), pinKeyHex, msg.getString(ksnField));
        } else {
            log.debug("No KSN defined, doing Zone PIN encryption");
            try {
                encryptedPINBlock = securityModule.encryptPINBlock(msg.getBytes(pinField), pinKey);
            } catch (JCEHandlerException e) {
                log.error("PIN Block encryption failed {}", e.toString(), e);
                return;
            }
        }
        sampler.addField(pinField, encryptedPINBlock);
    }

    protected void calculateARQC(ISO8583Sampler sampler) {
        final String hexKey = getImkac(), fieldNo = getIccField(), skdm = getSkdm(),
            txnData = getTxnData();
        final ISOMsg msg = sampler.getRequest();

        if (fieldNo == null || fieldNo.isEmpty() || !msg.hasField(fieldNo)) {
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
        // First, collect all EMV data in a lookup map:
        final Map<String, String> emvData = new HashMap<>();
        final String arqcFieldNo;
        try {
            final ISOComponent emvField = msg.getComponent(fieldNo);
            arqcFieldNo = String.format("%s.%d", fieldNo, emvField.getMaxField()+1);

            for (Object c : emvField.getChildren().values()) {
                if (c instanceof ISOTaggedField) {
                    ISOTaggedField f = (ISOTaggedField) c;
                    if (f.getValue() instanceof String)
                        emvData.put(f.getTag(), (String)f.getValue());
                    else if (f.getBytes() != null && f.getBytes().length != 0)
                        emvData.put(f.getTag(), ISOUtil.byte2hex(f.getBytes()));
                } else {
                    log.debug("Ignoring non-tagged field {}", c);
                }
            }
        } catch (ISOException e) {
            log.error("ARQC input extraction failed {}", e.toString(), e);
            return;
        }
        // Next, build input data from explicit data or message fields:
        final StringBuilder transactionData = new StringBuilder();
        if (txnData != null && !txnData.isEmpty()) {
            transactionData.append(txnData);
        } else { // automatically extract input tags from EMV data
            final String[] arqcInputTags = JMeterUtils.getPropDefault(ARQC_INPUT_TAGS,
                    "9F02,9F03,9F1A,95,5F2A,9A,9C,9F37,82,9F36,9F10"
            ).split(DELIMITER_REGEX);

            for (String tag : arqcInputTags) {
                transactionData.append(emvData.getOrDefault(tag, ""));
            }
        }

        // Lastly, the actual calculation:
        final String arqc = securityModule.calculateARQC(MKDMethod.OPTION_A,
            SKDMethod.valueOf(getSkdm()), hexKey,
            emvData.getOrDefault(APPLICATION_PRIMARY_ACCOUNT_NUMBER_0x5A.getTagNumberHex(), getPan()),
            emvData.getOrDefault(APPLICATION_PRIMARY_ACCOUNT_NUMBER_SEQUENCE_NUMBER_0x5F34.getTagNumberHex(), getPsn()),
            emvData.getOrDefault(APPLICATION_TRANSACTION_COUNTER_0x9F36.getTagNumberHex(), ""),
            emvData.getOrDefault(UNPREDICTABLE_NUMBER_0x9F37.getTagNumberHex(), ""),
            transactionData.toString());

        sampler.addField(arqcFieldNo, arqc, APPLICATION_CRYPTOGRAM_0x9F26.getTagNumberHex());
    }

    public String getMacAlgorithm() { return getPropertyAsString(MACALGORITHM); }
    public void setMacAlgorithm(String macAlgorithm) { setProperty(MACALGORITHM, macAlgorithm); }

    public String getMacKey() { return getPropertyAsString(MACKEY); }
    public void setMacKey(String macKey) { setProperty(MACKEY, macKey); }

    public String getMacField() { return getPropertyAsString(MACFIELD); }
    public void setMacField(String macField) { setProperty(MACFIELD, macField); }

    public String getPinKey() { return getPropertyAsString(PINKEY); }
    public void setPinKey(String pinKey) { setProperty(PINKEY, pinKey); }

    public String getPinField() { return getPropertyAsString(PINFIELD); }
    public void setPinField(String pinField) { setProperty(PINFIELD, pinField); }

    public String getKsnField() { return getPropertyAsString(KSNFIELD); }
    public void setKsnField(String ksnField) { setProperty(KSNFIELD, ksnField); }

    public String getIccField() { return getPropertyAsString(ICCFIELD); }
    public void setIccField(String iccField) { setProperty(ICCFIELD, iccField); }

    public String getImkac() { return getPropertyAsString(IMKAC); }
    public void setImkac(String imkac) { setProperty(IMKAC, imkac); }

    public String getSkdm() { return getPropertyAsString(SKDM); }
    public void setSkdm(String skdm) { setProperty(SKDM, skdm); }

    public String getPan() { return getPropertyAsString(PAN); }
    public void setPan(String pan) { setProperty(PAN, pan); }

    public String getPsn() { return getPropertyAsString(PSN); }
    public void setPsn(String psn) { setProperty(PSN, psn); }

    public String getTxnData() { return getPropertyAsString(TXNDATA); }
    public void setTxnData(String txnData) { setProperty(TXNDATA, txnData); }
}
