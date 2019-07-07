package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.ISOMsg;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ISO8583CryptoTest extends ISO8583TestBase {

    ISO8583Crypto instance = new ISO8583Crypto();
    ISO8583Sampler sampler = new ISO8583Sampler();
    ISO8583Config config = getDefaultTestConfig();

    @Before
    public void setup() {
        ctx.context.setCurrentSampler(sampler);
        sampler.addTestElement(config);
        sampler.setFields(asMessageFields(getTestMessage()));
    }

    @Test
    public void shouldNotCalculateMACWithMissingValues() {
        instance.setMacKey("");
        instance.setMacAlgorithm("DESEDE");
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertFalse(msg.hasAny(new int[]{instance.MAC_FIELD_NO, 2*instance.MAC_FIELD_NO}));

        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.setMacAlgorithm("");
        instance.process();
        msg = sampler.getRequest();
        assertFalse(msg.hasAny(new int[]{instance.MAC_FIELD_NO, 2*instance.MAC_FIELD_NO}));
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
    public void shouldCalculateMAC() {
        instance.setMacAlgorithm("DESEDE");
        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(instance.MAC_FIELD_NO));
        assertTrue(msg.getString(instance.MAC_FIELD_NO).matches("[0-9A-F]{16}"));
    }
}
