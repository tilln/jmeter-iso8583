package nz.co.breakpoint.jmeter.iso8583;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Key;
import javax.crypto.spec.SecretKeySpec;
import org.apache.jmeter.util.JMeterUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.core.SimpleConfiguration;
import org.jpos.iso.ISOUtil;
import org.jpos.security.*;
import org.jpos.security.jceadapter.JCEHandler;
import org.jpos.security.jceadapter.JCEHandlerException;
import org.jpos.security.jceadapter.JCESecurityModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583TestElement.KSN_DESCRIPTOR;

/** Adapter for jPOS JCESecurityModule with convenience wrapper methods
 */
public class SecurityModule extends JCESecurityModule {

    private static final Logger log = LoggerFactory.getLogger(SecurityModule.class);

    public SecurityModule() {
        super();
        Configuration cfg = new SimpleConfiguration();
        cfg.put("provider", BouncyCastleProvider.class.getName());
        cfg.put("rebuildlmk", "true"); // generate some keys rather than from "lmk" file
        try {
            setConfiguration(cfg);
        } catch (ConfigurationException shouldNotHappen) { // config is correct (unless BC is missing)
            shouldNotHappen.printStackTrace();
        }
    }

    protected Key formDESKey(String hex) {
        return new SecretKeySpec(ISOUtil.hex2byte(hex), hex.length() == 16 ? "DES" : "DESede");
    }

    public String encryptPINBlock(byte[] clearPinBlock, Key clearPinKey) throws JCEHandlerException {
        return ISOUtil.byte2hex(this.jceHandler.encryptData(clearPinBlock, clearPinKey));
    }

    public String generateMAC(byte[] packedMsg, Key clearMacKey, String macAlgorithm) throws JCEHandlerException {
        return ISOUtil.byte2hex(this.jceHandler.generateMAC(packedMsg, clearMacKey, macAlgorithm));
    }

    public String calculateARQC(MKDMethod mkdm, SKDMethod skdm, String clearMKAC,
            String accountNo, String accntSeqNo, String atc, String upn, String transData) {
        log.debug("ARQC input '{}'", transData);
        try {
            SecureDESKey mkac = formKEYfromClearComponents(LENGTH_DES3_2KEY, TYPE_MK_AC, clearMKAC);
            return ISOUtil.byte2hex(calculateARQC(mkdm, skdm, mkac, accountNo, accntSeqNo,
                ISOUtil.hex2byte(atc), ISOUtil.hex2byte(upn), ISOUtil.hex2byte(transData)));
        } catch (SMException e) {
            log.error("ARQC calculation failed {}", e.toString(), e);
        }
        return "";
    }

    protected KeySerialNumber parseKSN(String ksn) {
        final String ksnDescriptor = JMeterUtils.getPropDefault(KSN_DESCRIPTOR, "6-5-5");

        if (!ksnDescriptor.matches("[0-9]+-[0-9]+-[0-9]+")) {
            log.error("Invalid KSN Descriptor '{}'", ksnDescriptor);
            return null;
        }
        String[] digits = ksnDescriptor.split("-");
        int deviceIdStart = Integer.parseInt(digits[0]);
        int deviceIdEnd = deviceIdStart + Integer.parseInt(digits[1]);
        int totalDigits = deviceIdEnd + Integer.parseInt(digits[2]);

        final String baseKeyID = ksn.substring(0, deviceIdStart),
            deviceID = ksn.substring(deviceIdStart, deviceIdEnd),
            transactionCounter = ksn.substring(deviceIdEnd, totalDigits);

        return new KeySerialNumber(baseKeyID, deviceID, transactionCounter);
    }

    public String encryptPINBlock(byte[] clearPINBlock, String clearBDK, String keySerialNumber) {
        try {
            KeySerialNumber ksn = parseKSN(keySerialNumber);
            SecureDESKey bdk = formKEYfromClearComponents(LENGTH_DES3_2KEY, TYPE_BDK, clearBDK);

            // https://github.com/jpos/jPOS/blob/v2_1_3/jpos/src/main/java/org/jpos/security/jceadapter/JCESecurityModule.java#L2452-L2453
            byte[] derivedKey = calculateDerivedKey(ksn, bdk, true, false);
            log.debug("UDK={}", ISOUtil.byte2hex(derivedKey));

            byte[] translatedPINBlock = specialEncrypt(clearPINBlock, derivedKey);
            return ISOUtil.byte2hex(translatedPINBlock);
        } catch (SMException e) {
            log.error("DUKPT PIN Block encryption failed {}", e.toString(), e);
        }
        return "";
    }

    public String calculateCVV(String accountNo, String cvk, String expDate, String serviceCode) {
        try {
            return calculateCVD(accountNo, formDESKey(cvk), expDate, serviceCode);
        } catch (Exception e) {
            log.error("Failed to calculate CVV", e);
        }
        return "";
    }

    public String calculateKeyCheckValue(String clearKey) {
        try {
            return ISOUtil.byte2hex(calculateKeyCheckValue(formDESKey(clearKey)));
        } catch (SMException e) {
            log.error("Failed to calculate key check value", e);
        }
        return "";
    }

    public String encryptDESKey(String clearKey, String encryptingKey) {
        try {
            byte[] encryptedKey = this.jceHandler.encryptDESKey((short)(clearKey.length()*4),
                formDESKey(clearKey), formDESKey(encryptingKey));
            return ISOUtil.byte2hex(encryptedKey);
        } catch (JCEHandlerException e) {
            log.error("Failed to encrypt DES key {}", e.toString(), e);
        }
        return "";
    }

    public String calculatePINBlock(String pin, String format, String pan) {
        try {
            return ISOUtil.byte2hex(calculatePINBlock(pin, Byte.parseByte(format),
                EncryptedPIN.extractAccountNumberPart(pan)));
        } catch (SMException e) {
            log.error("Failed to calculate PIN Block", e);
        }
        return "";
    }

    public String generateDESKey(String length) {
        try {
            Short bits = Short.parseShort(length);
            Key key = this.jceHandler.generateDESKey(bits);
            // ensure expected key length (https://github.com/jpos/jPOS/blob/v2_1_3/jpos/src/main/java/org/jpos/security/jceadapter/JCEHandler.java#L111-L113):
            return ISOUtil.byte2hex(key.getEncoded()).substring(0, bits/4);
        } catch (JCEHandlerException e) {
            log.error("Failed to generate DES Key", e);
        }
        return "";
    }
}
