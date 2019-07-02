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
import org.jpos.iso.SunJSSESocketFactory;
import org.jpos.iso.channel.*;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.q2.Q2;
import org.jpos.q2.QFactory;
import org.jpos.q2.iso.ChannelAdaptor;
import org.jpos.q2.iso.QMUX;
import org.jpos.q2.iso.QServer;
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
        PORT = "port",
        QBEANKEY = "qbeankey";

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

    protected Element getChannelDescriptor(String name) {
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

        if (false) { // TODO SSL config
            channelDescriptor
                .addContent(new Element("property")
                    .setAttribute("name", "socketFactory")
                    .setAttribute("value", SunJSSESocketFactory.class.getName()))
                .addContent(new Element("property")
                    .setAttribute("name", "storepassword")
                    .setAttribute("value", "TODO"))
                .addContent(new Element("property")
                    .setAttribute("name", "keypassword")
                    .setAttribute("value", "TODO"))
                .addContent(new Element("property")
                    .setAttribute("name", "keystore")
                    .setAttribute("value", "TODO"));
        }
        return channelDescriptor;
    }

    // Registers ChannelAdaptor <name>-channel and BaseChannel channel.<name>-channel
    protected ChannelAdaptor configureChannel(String name) {
        ChannelAdaptor channelAdaptor = NameRegistrar.getIfExists(name);
        if (channelAdaptor != null) return channelAdaptor;

        // Build QBean deployment descriptor in memory
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/channel_adaptor.adoc
        Element descriptor = new Element("channel-adaptor")
            .setAttribute("name", name+"-channel")
            .addContent(getChannelDescriptor(name))
            .addContent(new Element("in").addContent(name+"-send"))
            .addContent(new Element("out").addContent(name+"-receive"))
            .addContent(new Element("reconnect-delay").addContent(Long.toString(10000))) // TODO configurable
            .addContent(new Element("wait-for-workers-on-stop").addContent("yes"));

        channelAdaptor = (ChannelAdaptor) deployAndStart(descriptor);
        return channelAdaptor;
    }

    // Registers QServer <name>-server and ISOServer server.<name>-server
    protected QServer configureServer(String name) {
        QServer qserver = NameRegistrar.getIfExists(name);
        if (qserver != null) return qserver;

        // Build QBean deployment descriptor in memory
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/qserver.adoc
        Element descriptor = new Element("qserver")
            .setAttribute("name", name+"-server")
            .addContent(getChannelDescriptor(name))
            .addContent(new Element("attr")
                .setAttribute("name", "port")
                .setAttribute("type", Integer.class.getName())
                .addContent(getPort()))
            .addContent(new Element("in").addContent(name+"-receive"))
            .addContent(new Element("out").addContent(name+"-send"))
            .addContent(new Element("ready").addContent(name+".ready"));

        qserver = (QServer) deployAndStart(descriptor);
        return qserver;
    }

    // Registers QMUX mux.<key>-mux and connects with <key>-receive and <key>-send Space queues
    // Would usually be called after configureChannel or configureServer.
    protected QMUX configureMux(String key) {
        String muxName = key+"-mux";
        QMUX mux = NameRegistrar.getIfExists("mux."+muxName);
        if (mux != null) return mux;

        // Build QBean deployment descriptor in memory
        // (note the in/out queues need to be cross-wired):
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/qmux.adoc
        Element descriptor = new Element("qmux")
            .setAttribute("name", muxName)
            .addContent(new Element("in").addContent(key+"-receive"))
            .addContent(new Element("out").addContent(key+"-send"))
            .addContent(new Element("unhandled").addContent(key+"-unhandled"))
            .addContent(new Element("ready").addContent(key+".ready"));

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


    // synchronized to avoid race conditions with multiple threads starting more than one Q2
    protected synchronized void startQ2() {
        log.debug("Waiting for Q2 lock");
        log.debug("Got Q2 lock");
        q2 = Q2.getQ2();
        log.debug("Got Q2 instance");
        if (q2 == null) {
            log.debug("Creating Q2");
            q2 = new Q2();
            q2.getLog().setLogger(null); // quieten it TODO configure
        }
        if (!q2.running()) {
            log.info("Starting Q2");
            q2.start();
            log.debug("Started Q2");
            boolean ready = q2.ready(JMeterUtils.getPropDefault("jmeter.iso8583.q2startup", 2000)); // TODO give it 2 seconds to start up
            log.debug("Q2 running");
            if (!ready) {
                log.error("Failed to start up Q2");
            }
        }
    }

    public String getMuxName() {
        return getPropertyAsString(QBEANKEY)+"-mux";
    }

    public String getServerName() {
        return getPropertyAsString(QBEANKEY)+"-server";
    }

    public String getChannelName() {
        return getPropertyAsString(QBEANKEY)+"-channel";
    }

    @Override
    public void testStarted() {
        startQ2();

        // allow for multiple QBean instances with distinct names
        String qbeanKey = String.format("jmeter-%08x", hashCode());
        setProperty(new StringProperty(QBEANKEY, qbeanKey));
        log.debug("Setting up QBeans "+qbeanKey);

        if (getHost().isEmpty()) {
            configureServer(qbeanKey);
        } else {
            configureChannel(qbeanKey);
        }
        configureMux(qbeanKey);
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
