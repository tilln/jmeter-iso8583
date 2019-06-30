package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.channel.XMLChannel;
import org.jpos.q2.iso.ChannelAdaptor;
import org.jpos.q2.iso.QMUX;
import org.jpos.util.NameRegistrar;
import org.junit.*;
import static org.junit.Assert.*;

public class ISO8583ConfigTest extends ISO8583TestBase {
    static ISO8583Config instance;

    @BeforeClass
    public static void setup() {
        instance = getDefaultTestConfig();
        instance.testStarted(); // make sure Q2 is running
    }

    @AfterClass
    public static void teardown() {
        instance.testEnded();
    }

    @Test
    public void shouldKnowJPOSChannels() {
        instance = new ISO8583Config();
        instance.setClassname("XMLChannel");
        assertEquals(XMLChannel.class.getName(), instance.getFullChannelClassName());
    }

    @Test
    public void shouldCreateChannel() {
        ChannelAdaptor channelAdaptor = instance.configureChannel("jmeter");
        assertNotNull(channelAdaptor);
        assertEquals("jmeter", channelAdaptor.getName());
        assertEquals(getDefaultTestConfig().getHost(), channelAdaptor.getHost());
        assertEquals(getDefaultTestConfig().getPortAsInt(), channelAdaptor.getPort());
        assertNotNull(NameRegistrar.getIfExists("jmeter"));
        assertTrue(channelAdaptor.running());
    }

    @Test
    public void shouldCreateMux() {
        QMUX mux = instance.configureMux("jmeter");
        assertNotNull(mux);
        assertEquals("jmeter-mux", mux.getName());
        assertNotNull(NameRegistrar.getIfExists("mux.jmeter-mux"));
        assertTrue(mux.running());
    }
}
