package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.junit.*;
import static org.junit.Assert.*;

public class ISO8583SamplerIT extends ISO8583TestBase {
    @ClassRule
    public static Q2ServerResource q2server = new Q2ServerResource();

    ISO8583Sampler instance = new ISO8583Sampler();
    static ISO8583Config config = getDefaultTestConfig();

    @BeforeClass
    public static void setup() {
        config.testStarted(); // starts up Q2
    }

    @AfterClass
    public static void tearDown() {
        config.testEnded();
    }

    @Test
    public void shouldReceiveResponse() {
        instance.addTestElement(config);
        ISOMsg msg = getTestMessage();
        instance.setFields(asMessageFields(msg));
        instance.setMacAlgorithm("");
        instance.setTimeout(2000);
        SampleResult res = instance.sample(new Entry());
        assertNotNull(res);
        assertFalse(res.getResponseDataAsString().isEmpty());
        ISOMsg response = xmlToIsoMsg(res.getResponseDataAsString());
        assertEquals(msg.getString(11), response.getString(11));
    }

}
