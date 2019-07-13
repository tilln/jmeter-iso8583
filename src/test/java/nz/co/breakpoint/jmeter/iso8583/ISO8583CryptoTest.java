package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.ISOMsg;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ISO8583CryptoTest extends ISO8583TestBase {

    ISO8583Crypto instance = new ISO8583Crypto();
    ISO8583Sampler sampler = new ISO8583Sampler();
    ISO8583Config config = getDefaultTestConfig();

    static final int[] possibleMACFields = new int[]{
        ISO8583Crypto.MAC_FIELD_NO, 2*ISO8583Crypto.MAC_FIELD_NO, 3*ISO8583Crypto.MAC_FIELD_NO};

    @Before
    public void setup() {
        ctx.context.setCurrentSampler(sampler);
        sampler.addTestElement(config);
        sampler.setFields(asMessageFields(getDefaultTestMessage()));
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
}
