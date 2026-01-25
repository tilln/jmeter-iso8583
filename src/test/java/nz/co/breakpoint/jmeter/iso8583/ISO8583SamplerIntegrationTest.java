package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.jpos.iso.ISOMsg;
import org.junit.*;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ISO8583SamplerIntegrationTest extends ISO8583TestBase {
    @ClassRule
    public static Q2ServerResource q2server = new Q2ServerResource();

    ISO8583Sampler instance;
    static ISO8583Config config = getDefaultTestConfig();

    @BeforeClass
    public static void setupClass() {
        config.testStarted(); // shares the Q2 instance with Q2ServerResource
    }

    @AfterClass
    public static void tearDownClass() {
        config.testEnded();
    }

    @Before
    public void setup() {
        instance = new ISO8583Sampler();
        configureSampler(instance, config, asMessageFields(getDefaultTestMessage()));
    }

    @Test
    public void shouldReceiveResponse() {
        instance.addField("48.1", "1122334455667788", "9f26");
        instance.setTimeout(30000); // long enough for CI/CD build
        ISOMsg msg = instance.getRequest();
        SampleResult res = instance.sample(new Entry());
        assertNotNull(res);
        assertFalse(res.getResponseDataAsString().isEmpty());
        assertEquals("OK", res.getResponseMessage());
        ISOMsg response = instance.getResponse();
        assertEquals(msg.getString(11), response.getString(11));
        assertEquals("1122334455667788", response.getString("48.1"));
    }

    @Test
    public void shouldAllowFireAndForget() {
        instance.addField("35", ""); // simulate delay
        instance.setTimeout(0); // indicates fire-and-forget
        SampleResult res = instance.sample(new Entry());
        assertNotNull(res);
        assertTrue(res.isSuccessful());
        assertTrue(res.getResponseDataAsString().isEmpty());
        assertEquals(0, res.getBytesAsLong());
        assertEquals("No response", res.getResponseMessage());
        assertNull(instance.getResponse());
    }

    @Test
    public void shouldFailOnTimeout() {
        instance.addField("35", ""); // simulate delay
        instance.setTimeout(500);
        SampleResult res = instance.sample(new Entry());
        assertNotNull(res);
        assertFalse(res.isSuccessful());
        assertTrue(res.getResponseDataAsString().isEmpty());
        assertEquals(0, res.getBytesAsLong());
        assertEquals("Timeout", res.getResponseMessage());
        assertNull(instance.getResponse());
    }

    @Test
    public void shouldValidateResponse() {
        instance.setTimeout(5000);
        instance.setResponseCodeField("39");
        instance.setSuccessResponseCode("10, 00");
        SampleResult res = instance.sample(new Entry());
        assertTrue(res.isSuccessful());
        instance.setSuccessResponseCode("99,88");
        res = instance.sample(new Entry());
        assertFalse(res.isSuccessful());
    }

    @Test // Issue 24
    public void testConcurrency() throws InterruptedException {
        AtomicBoolean exceptionThrown = new AtomicBoolean(false);
        for (int i=0; i<100; i++) {
            Thread t = new Thread(() -> {
                for (int j=0; j<100; j++) {
                    try {
                        instance.sample(null);
                    } catch (ConcurrentModificationException e) {
                        e.printStackTrace();
                        exceptionThrown.set(true);
                    }
                    Thread.yield();
                }
            });
            t.start();
            t.join();
        }
        assertFalse("Expected no ConcurrentModificationException", exceptionThrown.get());
    }
}
