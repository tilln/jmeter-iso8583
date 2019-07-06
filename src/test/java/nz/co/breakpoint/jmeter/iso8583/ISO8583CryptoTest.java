package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ISO8583CryptoTest extends ISO8583TestBase {

    ISO8583Crypto instance = new ISO8583Crypto();
    ISO8583Sampler sampler = new ISO8583Sampler();

    @Before
    public void setup() {
        sampler.addTestElement(getDefaultTestConfig());
        sampler.setFields(asMessageFields(getTestMessage()));
    }

    @Test
    public void shouldCalculateMAC() throws ISOException {
        instance.setMacAlgorithm("DESEDE");
        instance.setMacKey(DEFAULT_KEY);
        instance.calculateMAC(sampler);
        ISOMsg msg = sampler.getRequest();
        assertNotNull(msg);
        assertTrue(msg.hasField(instance.MAC_FIELD_NO));
        assertTrue(msg.getString(instance.MAC_FIELD_NO).matches("[0-9A-F]{16}"));
    }
}
