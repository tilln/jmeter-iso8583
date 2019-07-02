package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.channel.XMLChannel;
import org.jpos.q2.iso.ChannelAdaptor;
import org.jpos.q2.iso.QMUX;
import org.jpos.q2.iso.QServer;
import org.jpos.util.NameRegistrar;
import org.junit.*;
import static org.junit.Assert.*;

public class ISO8583ConfigTest extends ISO8583TestBase {
    static ISO8583Config instance;

    @Before
    public void setup() {
        instance = getDefaultTestConfig();
        instance.startQ2();
        assert instance.q2.ready(2000);
    }

    @After
    public void teardown() {
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
        assertEquals("jmeter-channel", channelAdaptor.getName());
        assertEquals(getDefaultTestConfig().getHost(), channelAdaptor.getHost());
        assertEquals(getDefaultTestConfig().getPortAsInt(), channelAdaptor.getPort());
        assertNotNull(NameRegistrar.getIfExists("jmeter-channel"));
        assertNotNull(NameRegistrar.getIfExists("channel.jmeter-channel"));
        assertTrue(channelAdaptor.running());
    }

    @Test
    public void shouldCreateServer() {
        QServer qserver = instance.configureServer("jmeter");
        assertNotNull(qserver);
        assertEquals("jmeter-server", qserver.getName());
        assertNotNull(NameRegistrar.getIfExists("jmeter-server"));
        assertNotNull(NameRegistrar.getIfExists("server.jmeter-server"));
        assertTrue(qserver.running());
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
