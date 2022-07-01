package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.core.Configuration;
import org.jpos.core.SimpleConfiguration;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.junit.*;
import static org.junit.Assert.*;

public class GroovyRequestListenerTest {
    GroovyRequestListener instance = new GroovyRequestListener();

    @Before
    public void setup() {
        Configuration cfg = new SimpleConfiguration();
        cfg.put("source", "src/test/resources/listener.groovy");
        instance.setConfiguration(cfg);
    }

    @Test
    public void shouldModifyReceivedMessage() throws ISOException {
        ISOMsg msg = new ISOMsg("0800");
        boolean processed = instance.process(null, msg);
        assertTrue(processed);
        assertEquals("0810", msg.getMTI());
    }
}
