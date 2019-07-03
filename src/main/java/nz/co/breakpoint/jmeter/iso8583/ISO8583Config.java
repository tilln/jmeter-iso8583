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
import org.jpos.iso.*;
import org.jpos.iso.channel.*;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.q2.Q2;
import org.jpos.q2.QBeanSupport;
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
        PORT = "port";

    // Internal property name for distinct QBean names if there are more than one ISO8583Config instance:
    protected static final String CONFIGKEY = "configKey";

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
                .setAttribute("value", getPort()))
            .addContent(new Element("property")
                // Setting the keep-alive (true/false) would set the low level SO_KEEPALIVE flag at the socket level
                // for situations where no network management messages are exchanged.
                .setAttribute("name", "keep-alive")
                .setAttribute("value", "true"));

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

    // Registers ChannelAdaptor <key>-channel and BaseChannel channel.<key>-channel
    protected ChannelAdaptor startChannelAdaptor() {
        String key = getPropertyAsString(CONFIGKEY);

        // Build QBean deployment descriptor in memory
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/channel_adaptor.adoc
        Element descriptor = new Element("channel-adaptor")
            .setAttribute("name", getChannelAdaptorName())
            .setAttribute("logger", "")
            .addContent(getChannelDescriptor(key))
            .addContent(new Element("in").addContent(key+"-send"))
            .addContent(new Element("out").addContent(key+"-receive"))
            .addContent(new Element("reconnect-delay").addContent(Long.toString(10000))) // TODO configurable
            .addContent(new Element("wait-for-workers-on-stop").addContent("yes"));

        ChannelAdaptor channelAdaptor = (ChannelAdaptor) deployAndStart(descriptor);
        log.debug("Deployed ChannelAdaptor {}", channelAdaptor);
        return channelAdaptor;
    }

    // Registers QServer <key>-server and ISOServer server.<key>-server
    protected QServer startQServer() {
        String key = getPropertyAsString(CONFIGKEY);

        // Build QBean deployment descriptor in memory
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/qserver.adoc
        Element descriptor = new Element("qserver")
            .setAttribute("name", getQServerName())
            .setAttribute("logger", "")
            .addContent(getChannelDescriptor(key))
            .addContent(new Element("attr")
                .setAttribute("name", "port")
                .setAttribute("type", Integer.class.getName())
                .addContent(getPort()))
            .addContent(new Element("in").addContent(key+"-send"))
            .addContent(new Element("out").addContent(key+"-receive"))
            .addContent(new Element("ready").addContent(key+".ready"));

        QServer qserver = (QServer) deployAndStart(descriptor);
        log.debug("Deployed QServer {}", qserver);
        return qserver;
    }

    // Registers QMUX mux.<key>-mux and connects with <key>-receive and <key>-send Space queues
    // Would usually be called *after* startChannelAdaptor or startQServer.
    protected QMUX startMux() {
        String key = getPropertyAsString(CONFIGKEY);

        // Build QBean deployment descriptor in memory
        // (note the in/out queues need to be cross-wired):
        // https://github.com/jpos/jPOS/blob/v2_1_3/doc/src/asciidoc/ch08/qmux.adoc
        Element descriptor = new Element("qmux")
            .setAttribute("name", getMuxName())
            .setAttribute("logger", "")
            .addContent(new Element("in").addContent(key+"-receive"))
            .addContent(new Element("out").addContent(key+"-send"))
            .addContent(new Element("unhandled").addContent(key+"-unhandled"))
            .addContent(new Element("ready").addContent(key+".ready"));

        QMUX mux = (QMUX) deployAndStart(descriptor);
        log.debug("Deployed QMUX {}", mux);
        return mux;
    }

    // Mimic Q2 deployment of a descriptor file, followed by starting the QBean,
    // https://github.com/jpos/jPOS/blob/v2_1_3/jpos/src/main/java/org/jpos/q2/Q2.java#L560
    // but using more accessible QFactory methods:
    protected Object deployAndStart(Element descriptor) {
        QFactory qFactory = q2.getFactory();
        try {
            log.debug("Deploying {}", descriptor.getName());
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

    protected void stopChannelAdaptor() {
        stopAndUndeploy(getChannelAdaptorName());
    }

    protected void stopQServer() {
        stopAndUndeploy(getQServerName());
    }

    protected void stopMux() {
        stopAndUndeploy(getMuxName());
    }

    protected void stopAndUndeploy(String key) {
        QFactory qFactory = q2.getFactory();
        try {
            log.debug("Undeploying {}", key);
            Object qbean = new QBeanSupport(); // TODO
            ObjectName objectName = new ObjectName(Q2.QBEAN_NAME+key);
            qFactory.destroyQBean(q2, objectName, qbean);
        } catch (MalformedObjectNameException | InstanceAlreadyExistsException | InstanceNotFoundException |
                MBeanException | NotCompliantMBeanException | InvalidAttributeValueException |
                ReflectionException | ClassNotFoundException | InstantiationException |
                IllegalAccessException | MalformedURLException e) {
            log.error("Failed to undeploy {}", key, e);
        }
    }

    // synchronized to avoid race conditions with multiple instances starting more than one Q2
    // TODO double check if JMeter engine does single-threaded config initialisation
    protected synchronized void startQ2() {
        q2 = Q2.getQ2();
        if (q2 == null) {
            log.debug("Creating Q2");
            q2 = new Q2();
            if (!log.isDebugEnabled()) {
                q2.getLog().setLogger(null); // quieten it TODO configure
            }
        }
        if (q2.running()) {
            log.debug("Q2 running");
        } else {
            log.info("Starting Q2");
            q2.start();
            log.debug("Started Q2");
            boolean ready = q2.ready(JMeterUtils.getPropDefault("jmeter.iso8583.q2startup", 2000)); // TODO give it 2 seconds to start up
            log.debug("Q2 ready: {}", ready);
            if (!ready) {
                log.error("Failed to start up Q2");
            }
        }
    }

    protected synchronized void stopQ2() {
        if (q2.running()) {
            log.debug("Shutting down Q2");
            q2.stop();
        }
    }

    public String getMuxName() { return getPropertyAsString(CONFIGKEY)+"-mux"; }

    public String getQServerName() { return getPropertyAsString(CONFIGKEY)+"-server"; }

    public String getChannelAdaptorName() { return getPropertyAsString(CONFIGKEY)+"-channel"; }

    protected boolean isServer() { return getHost() == null || getHost().isEmpty(); }

    @Override
    public void testStarted() {
        startQ2();

        // Create a distinct key for naming this element's QBeans.
        // Needs to be a JMeter property so it gets cloned for the sampler's addTestElement().
        String configKey = String.format("jmeter-%08x", hashCode());
        setProperty(CONFIGKEY, configKey);
        log.debug("Setting up QBeans {}", configKey);

        if (isServer()) {
            startQServer();
            // TODO wait for incoming connection
            while (true) {
                ISOUtil.sleep(1000);
                try {
                    if (ISOServer.getServer(getQServerName()).getLastConnectedISOChannel() != null) break;
                } catch (NameRegistrar.NotFoundException e) {
                    e.printStackTrace();
                }
            }
        } else {
            startChannelAdaptor();
        }
        startMux();
    }

    @Override
    public void testEnded() {
        log.debug("Shutting down QBeans");

        stopMux();
        if (isServer()) {
            stopQServer();
        } else {
            stopChannelAdaptor();
        }
        stopQ2();
    }

    @Override
    public void testStarted(String s) {
        testStarted();
    }

    @Override
    public void testEnded(String s) {
        testEnded();
    }

    // Accessors for mapping to TestBean GUI elements...
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
}
