package nz.co.breakpoint.jmeter.iso8583;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import org.apache.jmeter.util.JMeterUtils;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Crypto.skdMethods;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583TestElement.ARQC_INPUT_TAGS;

public class ISO8583CryptoTest extends ISO8583TestBase {

    ISO8583Crypto instance = new ISO8583Crypto();
    ISO8583Sampler sampler = new ISO8583Sampler();

    static final int[] possibleMACFields = new int[]{
        ISO8583Crypto.MAC_FIELD_NO, 2*ISO8583Crypto.MAC_FIELD_NO, 3*ISO8583Crypto.MAC_FIELD_NO};

    @Before
    public void setup() {
        configureSampler(sampler, getDefaultTestConfig(), asMessageFields(getDefaultTestMessage()));
    }

    @Test
    public void shouldNotEncryptPINBlockWithMissingValues() {
        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey("");
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertFalse(msg.hasField(instance.PIN_FIELD_NO));

        instance.setPinField("");
        instance.setPinKey(DEFAULT_3DES_KEY);
        instance.process();
        msg = sampler.getRequest();
        assertFalse(msg.hasField(instance.PIN_FIELD_NO));

        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey(DEFAULT_3DES_KEY);
        sampler.addField(String.valueOf(instance.PIN_FIELD_NO), "");
        instance.process();
        msg = sampler.getRequest();
        assertEquals("", msg.getString(instance.PIN_FIELD_NO));
    }

    @Test
    public void shouldEncryptPINBlock() {
        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey(DEFAULT_3DES_KEY);
        String clearPinBlock = "0000000000000000";
        sampler.addField(String.valueOf(instance.PIN_FIELD_NO), clearPinBlock);
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(instance.PIN_FIELD_NO));
        String pinBlock = msg.getString(instance.PIN_FIELD_NO);
        assertEquals(clearPinBlock.length(), pinBlock.length());
        assertTrue(msg.getString(instance.PIN_FIELD_NO).matches("[0-9A-F]{16}"));
    }

    @Test
    public void shouldNotCalculateMACWithMissingValues() {
        instance.setMacKey("");
        instance.setMacAlgorithm("DESEDE");
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertFalse(msg.hasAny(possibleMACFields));

        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.setMacAlgorithm("");
        instance.process();
        msg = sampler.getRequest();
        assertFalse(msg.hasAny(possibleMACFields));
    }

    @Test
    public void shouldAllowArbitraryMACField() {
        instance.setMacAlgorithm("DESEDE");
        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.setMacField("52");
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(52));
        assertTrue(msg.getString(52).matches("[0-9A-F]{16}"));
    }

    @Test
    public void shouldCalculateMACInLastField() {
        instance.setMacAlgorithm("DESEDE");
        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(64));
        assertTrue(msg.getString(64).matches("[0-9A-F]{16}"));
        assertEquals(msg.getMaxField(), 64);

        sampler.addField("70", "301");
        instance.process();
        msg = sampler.getRequest();
        assertTrue(msg.hasField(128));
        assertEquals(128, msg.getMaxField());

        sampler.addField("131", "HELLO");
        instance.process();
        msg = sampler.getRequest();
        assertTrue(msg.hasField(192));
        assertEquals(192, msg.getMaxField());
    }

    @Test
    public void shouldCalculateARQC() {
        sampler.setFields(asMessageFields(
            new MessageField("55.1", "ABCD", "9f1e"),
            new MessageField("55.2", "01", "9f36"),
            new MessageField("55.3", "11223344", "9f37")
        ));
        instance.setImkac(DEFAULT_3DES_KEY);
        instance.setSkdm(skdMethods[0]);
        instance.setIccField("55");
        instance.process();

        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField("55.4"));
        assertTrue(msg.getString("55.4").matches("[0-9A-F]{16}"));
    }

    @Test
    public void shouldHaveConfigurableARQCInputTags() {
        JMeterUtils.setProperty(ARQC_INPUT_TAGS, "9f37");
        instance.setImkac(DEFAULT_3DES_KEY);
        instance.setSkdm(skdMethods[0]);
        instance.setIccField("55");

        sampler.setFields(asMessageFields(
            new MessageField("55.1", "ABCD", "9f1e"),
            new MessageField("55.2", "01", "9f36"), // should be excluded from calculation
            new MessageField("55.3", "11223344", "9f37")
        ));
        instance.process();

        String arqc = sampler.getRequest().getString("55.4");
        assertNotNull(arqc);

        sampler.setFields(asMessageFields(
            new MessageField("55.1", "ABCD", "9f1e"),
            new MessageField("55.2", "99", "9f36"), // should not change the previous arqc
            new MessageField("55.3", "11223344", "9f37")
        ));
        instance.process();

        assertEquals(arqc, sampler.getRequest().getString("55.4"));
    }

    @Test
    public void shouldAppendAdditionalARQCInputTags() {
        sampler.setFields(asMessageFields(
            new MessageField("55.1", "ABCD", "9f1e"),
            new MessageField("55.2", "01", "9f36"),
            new MessageField("55.3", "11223344", "9f37")
        ));
        instance.setImkac(DEFAULT_3DES_KEY);
        instance.setSkdm(skdMethods[0]);
        instance.setIccField("55");

        instance.setTxnData("ADDITIONAL");
        instance.process();

        instance.setTxnData("DIFFERENT");// should result in a different arqc
        instance.process();

        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField("55.4"));
        assertTrue(msg.hasField("55.5"));
        assertNotEquals(msg.getString("55.4"), msg.getString("55.5"));
    }

    @Test
    public void shouldDUKPTEncryptPINBlock() {
        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey(DEFAULT_3DES_KEY);
        instance.setKsnField(String.valueOf(instance.KSN_FIELD_NO));
        String clearPinBlock = "041277cccddddeee";

        sampler.addField(String.valueOf(instance.PIN_FIELD_NO), clearPinBlock);
        sampler.addField(String.valueOf(instance.KSN_FIELD_NO), "9876543210E00001");
        instance.process();

        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(instance.PIN_FIELD_NO));
        String pinBlock = msg.getString(instance.PIN_FIELD_NO);
        assertEquals(clearPinBlock.length(), pinBlock.length());
        assertEquals("C0F224AD1647A8EB", pinBlock);
    }
}
