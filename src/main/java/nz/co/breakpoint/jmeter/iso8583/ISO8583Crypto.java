package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.security.Key;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.security.jceadapter.JCEHandler;
import org.jpos.security.jceadapter.JCEHandlerException;
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
        PINKEY = "pinKey";

    protected JCEHandler jceHandler;
    protected Key macKey, pinKey;

    public ISO8583Crypto() {
        try {
            jceHandler = new JCEHandler(BouncyCastleProvider.class.getName());
        } catch (JCEHandlerException e) {
            log.error("Failed to create JCEHandler", e);
        }
    }

    @Override
    public void process() {
        JMeterContext context = getThreadContext();
        Sampler current = context.getCurrentSampler();
        if (!(current instanceof ISO8583Sampler)) return;

        log.debug("Processing sampler '{}'", current.getName());
        ISO8583Sampler sampler = (ISO8583Sampler)current;

        if (jceHandler == null) {
            log.warn("JCEHandler undefined");
            return;
        }
        encryptPINBlock(sampler);
        // TODO how to calculate other cryptograms: ARQC, DUKPT?
        calculateMAC(sampler);
    }

    protected void calculateMAC(ISO8583Sampler sampler) {
        String macKeyHex = getMacKey(), macAlgorithm = getMacAlgorithm();

        if (macAlgorithm == null || macAlgorithm.isEmpty()) {
            log.debug("No MAC algorithm defined, skipping MAC calculation");
            return;
        }
        if (macKeyHex == null || macKeyHex.isEmpty()) {
            log.debug("No MAC key defined, skipping MAC calculation");
            return;
        }
        if (macKeyHex.length() != 32 && macKeyHex.length() != 48) {
            log.warn("Incorrect MAC key size {}", macKeyHex);
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
        ISOMsg msg = sampler.getMessage();
        if (msg.getPackager() == null) {
            log.error("Packager undefined, skipping MAC calculation");
            return;
        }
        int macField = msg.getMaxField() <= MAC_FIELD_NO ? MAC_FIELD_NO : 2*MAC_FIELD_NO;
        int macLength = ((ISOBasePackager)msg.getPackager()).getFieldPackager(macField).getLength();
        String dummyMac = String.format("%0" + 2*macLength + "d", 0);
        msg.set(macField, dummyMac);
        try {
            byte[] packedMsg = msg.pack();
            // cut off MAC bytes and don't include them in calculation:
            byte[] mac = jceHandler.generateMAC(Arrays.copyOf(packedMsg, packedMsg.length-macLength),
                    macKey, macAlgorithm);
            sampler.addField(String.valueOf(macField), ISOUtil.padright(ISOUtil.byte2hex(mac), 2*macLength, 'f'));
        } catch (ISOException e) {
            log.error("MAC calculation failed", e);
        }
    }

    protected void encryptPINBlock(ISO8583Sampler sampler) {
        String pinKeyHex = getPinKey(), pinField = getPinField();

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
                log.warn("Incorrect PIN key size {}", pinKeyHex);
                return;
        }
        ISOMsg msg = sampler.getMessage();
        if (!msg.hasField(pinField)) {
            log.debug("No PIN Block defined, skipping PIN Block encryption");
        }
        String clearPinBlock = msg.getString(pinField);

        try {
            byte[] encryptedPinBlock = jceHandler.encryptData(ISOUtil.hex2byte(clearPinBlock), pinKey);
            sampler.addField(pinField, ISOUtil.byte2hex(encryptedPinBlock));
        } catch (JCEHandlerException e) {
            log.error("PIN Block encryption failed", e);
        }
    }

    public String getMacAlgorithm() { return getPropertyAsString(MACALGORITHM); }
    public void setMacAlgorithm(String macAlgorithm) { setProperty(MACALGORITHM, macAlgorithm); }

    public String getMacKey() { return getPropertyAsString(MACKEY); }
    public void setMacKey(String macKey) { setProperty(MACKEY, macKey); }

    public String getPinKey() { return getPropertyAsString(PINKEY); }
    public void setPinKey(String pinKey) { setProperty(PINKEY, pinKey); }

    public String getPinField() { return getPropertyAsString(PINFIELD); }
    public void setPinField(String pinField) { setProperty(PINFIELD, pinField); }
}
