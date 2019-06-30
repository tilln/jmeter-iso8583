package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import javax.management.*;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.util.JMeterUtils;
import org.jdom2.Element;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.channel.*;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.q2.Q2;
import org.jpos.q2.QFactory;
import org.jpos.q2.iso.ChannelAdaptor;
import org.jpos.q2.iso.QMUX;
import org.jpos.util.NameRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ISO8583Config extends ConfigTestElement
        implements ISO8583TestElement, TestBean, Serializable, TestStateListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ISO8583Config.class);

    // JMeter Property names (appear in script files, so don't change):
    public static final String
        CLASSNAME = "classname",
        PACKAGER = "packager",
        HEADER = "header",
        HOST = "host",
        PORT = "port";

    // Lookup map of Channel classes that come with jPOS (for GUI dropdown):
    static final Map<String, String> channelClasses = new HashMap<>();
    static {
        for (Class<? extends BaseChannel> clazz :
            new Class[]{
                ASCIIChannel.class, AmexChannel.class, BASE24Channel.class, BASE24TCPChannel.class, BCDChannel.class,
                CSChannel.class, GICCChannel.class, GZIPChannel.class, HEXChannel.class, LogChannel.class,
                NACChannel.class, NCCChannel.class, PADChannel.class, PostChannel.class, RawChannel.class,
                RBPChannel.class, TelnetXMLChannel.class, VAPChannel.class, X25Channel.class, XMLChannel.class
        }) {
            channelClasses.put(clazz.getSimpleName(), clazz.getName());
        }
    }

    // For GUI...
    static String getDefaultChannelClass() { return getChannelClasses()[0]; }
    static String[] getChannelClasses() { return channelClasses.keySet().toArray(new String[]{}); }
    static final String[] macAlgorithms = new String[]{"", "DESEDE", "ISO9797ALG3MACWITHISO7816-4PADDING"};

    protected static transient Q2 q2;

    // Instantiate packager from config file
    public ISOBasePackager createPackager() {
        log.debug("Creating packager from '{}'", getPackager());
        try {
            return new GenericPackager(getPackager());
        } catch (ISOException e) {
            log.error("Packager configuration error", e.getNested());
            return null;
        }
    }

    // Get full class name, for instantiation
    public String getFullChannelClassName() {
        String className = getClassname();
        if (channelClasses.containsKey(className)) {
            return channelClasses.get(className);
        }
        return className;
    }

    public String getClassname() { return getPropertyAsString(CLASSNAME); }
    public void setClassname(String classname) { setProperty(new StringProperty(CLASSNAME, classname)); }

    public String getPackager() { return getPropertyAsString(PACKAGER);}
    public void setPackager(String packager) { setProperty(new StringProperty(PACKAGER, packager)); }

    public String getHeader() { return getPropertyAsString(HEADER); }
    public void setHeader(String header) { setProperty(new StringProperty(HEADER, header)); }

    public String getHost() { return getPropertyAsString(HOST); }
    public void setHost(String host) { setProperty(new StringProperty(HOST, host)); }

    public String getPort() { return getPropertyAsString(PORT); }
    public void setPort(String port) { setProperty(new StringProperty(PORT, port)); }
    public int getPortAsInt() { return Integer.parseInt(getPort()); }

    protected ChannelAdaptor configureChannel(String name) {
        ChannelAdaptor channelAdaptor = NameRegistrar.getIfExists(name);
        if (channelAdaptor != null) return channelAdaptor;

        // Build QBean deployment descriptor in memory:
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/channel_adaptor.adoc
        Element channelDescriptor = new Element("channel")
            .setAttribute("name", name)
            .setAttribute("class", getFullChannelClassName())
            .setAttribute("packager", GenericPackager.class.getName())
            .setAttribute("header", getHeader())
            .addContent(new Element("property")
                .setAttribute("name", "packager-config")
                .setAttribute("value", getPackager()))
            .addContent(new Element("property")
                .setAttribute("name", "host")
                .setAttribute("value", getHost()))
            .addContent(new Element("property")
                .setAttribute("name", "port")
                .setAttribute("value", getPort()));

        Element descriptor = new Element("channel-adaptor")
            .setAttribute("name", name)
            .addContent(channelDescriptor)
            .addContent(new Element("in").addContent(name+"-send"))
            .addContent(new Element("out").addContent(name+"-receive"))
            .addContent(new Element("reconnect-delay").addContent(Long.toString(10000))) // TODO configurable
            .addContent(new Element("wait-for-workers-on-stop").addContent("yes"));

        channelAdaptor = (ChannelAdaptor) deployAndStart(descriptor);
        return channelAdaptor;
    }

    protected QMUX configureMux(String channelName) {
        // append suffix to avoid QBean name clash:
        QMUX mux = NameRegistrar.getIfExists("mux."+channelName+"-mux");
        if (mux != null) return mux;

        // Build QBean deployment descriptor in memory
        // (note the in/out queues need to be cross-wired):
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/qmux.adoc
        Element descriptor = new Element("qmux")
            .setAttribute("name", channelName+"-mux")
            .addContent(new Element("in").addContent(channelName+"-receive"))
            .addContent(new Element("out").addContent(channelName+"-send"))
            .addContent(new Element("unhandled").addContent(channelName+"-unhandled"))
            .addContent(new Element("ready").addContent(channelName+".ready"));

        mux = (QMUX) deployAndStart(descriptor);
        return mux;
    }

    // Mimic Q2 deployment of a descriptor file, followed by starting the QBean,
    // https://github.com/jpos/jPOS/blob/v2_1_3/jpos/src/main/java/org/jpos/q2/Q2.java#L560
    // but using more accessible QFactory methods:
    protected synchronized Object deployAndStart(Element descriptor) {
        QFactory qFactory = q2.getFactory();
        try {
            Object qbean = qFactory.instantiate(q2, descriptor);
            ObjectInstance obj = qFactory.createQBean(q2, descriptor, qbean);
            qFactory.startQBean(q2, obj.getObjectName());
            return qbean;
        } catch (MalformedObjectNameException | InstanceAlreadyExistsException | InstanceNotFoundException |
                MBeanException | NotCompliantMBeanException | InvalidAttributeValueException |
                ReflectionException | ClassNotFoundException | InstantiationException |
                IllegalAccessException | MalformedURLException | ConfigurationException e) {
            log.error("Failed to deploy {}", descriptor.getName(), e);
            return null;
        }
    }

    // TODO this may have to be synchronized to avoid race conditions with multiple threads starting more than one Q2
    @Override
    public void testStarted() {
        q2 = Q2.getQ2(1000);
        if (q2 == null) {
            log.info("Starting Q2");
            q2 = new Q2();
            q2.getLog().setLogger(null); // quieten it TODO configure
        }
        if (!q2.running()) {
            q2.start();
            boolean ready = q2.ready(JMeterUtils.getPropDefault("jmeter.iso8583.q2startup", 2000)); // TODO give it 2 seconds to start up
            if (!ready) {
                log.error("Failed to start up Q2");
            }
        }
        // TODO allow for multiple QBean instances with different names:
        configureChannel("jmeter");
        configureMux("jmeter");
    }

    @Override
    public void testEnded() {
        log.info("Shutting down Q2");
        q2.shutdown(true);
    }

    @Override
    public void testStarted(String s) {
        testStarted();
    }

    @Override
    public void testEnded(String s) {
        testEnded();
    }
}
