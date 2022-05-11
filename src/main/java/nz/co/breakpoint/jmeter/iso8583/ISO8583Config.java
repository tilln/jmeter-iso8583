package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.management.*;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.util.JMeterUtils;
import org.jdom2.Element;
import org.jdom2.output.XMLOutputter;
import org.jpos.iso.*;
import org.jpos.iso.channel.*;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.q2.Q2;
import org.jpos.q2.QBeanSupport;
import org.jpos.q2.QFactory;
import org.jpos.q2.iso.*;
import org.jpos.util.NameRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is effectively a wrapper for the jPOS Q2 container and associated QBeans configuration.
 * It manages either set of 3 components (depending on client or server mode):<ul>
 *   <li>ChannelAdaptor, Channel and QMUX</li>
 *   <li>QServer, Channel and QMUX</li>
 * </ul>
 * While normally those would be configured by placing corresponding XML files into a <i>deploy</i> folder,
 * here it is done dynamically via transforming configuration properties from the JMeter Test Plan
 * into in-memory deployment descriptors (JDOM Elements).
 *
 * These descriptors are then used to create and deploy QBeans at the test start and destroy them at the end.
 * Advanced, Channel-dependent configuration properties can be specified via name/value pairs.
 *
 * For example, <i>srcid</i> and <i>dstid</i> for
 * <a href="https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/iso/channel/VAPChannel.java#L236-L237">
 * VAPChannel's Base1Header</a><br>
 * For even more advanced use cases, above XML files may still be used and copied to the Q2 deploy folder.
 */
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
        REUSECONNECTION = "reuseConnection",
        MAXCONNECTIONS = "maxConnections",
        CONNECTIONSELECTION = "connectionSelection",
        CHANNELCONFIG = "channelConfig",
        REQUESTLISTENER = "requestListener",
        KEYSTORE = "keystore",
        STOREPASSWORD = "storePassword",
        KEYPASSWORD = "keyPassword",
        MTIMAPPING = "mtiMapping",
        MUXKEYCONFIG = "muxKeyConfig";

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

    public enum ConnectionSelection {
        LAST, // Last connected https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/q2/iso/QServer.java#L349
        RR, // Round robin https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/q2/iso/QServer.java#L384
        ALL; // All connected https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/q2/iso/QServer.java#L364

        // Tags must match ResourceBundle and appear in script files:
        public static ConnectionSelection fromTag(String connectionSelection) {
            return valueOf(connectionSelection.replaceFirst(CONNECTIONSELECTION+".", ""));
        }

        public static String[] tags() {
            return Arrays.stream(values()).map(ConnectionSelection::toTag).toArray(String[]::new);
        }

        public String toTag() {
            return CONNECTIONSELECTION + "." + toString();
        }
    }

    // For GUI...
    static String getDefaultConnectionSelection() { return ConnectionSelection.LAST.toTag(); }

    static String getDefaultChannelClass() { return getChannelClasses()[0]; }
    static String[] getChannelClasses() { return channelClasses.keySet().toArray(new String[]{}); }

    protected static transient Q2 q2;
    // Internal property name for distinct QBean names if there are more than one ISO8583Config instance:
    protected static final String CONFIGKEY = "configKey";
    protected static final String Q2_LOGGER = "Q2";

    static {
        org.jpos.util.Logger logger = org.jpos.util.Logger.getLogger(Q2_LOGGER);
        if (!logger.hasListeners())
            logger.addListener(new Slf4jLogListener());
    }

    // Instantiate packager from config file
    public ISOPackager createPackager() {
        String fileName = getPackager();
        if (fileName == null || fileName.isEmpty()) {
            log.warn("Packager config undefined");
            return null;
        }
        log.debug("Creating packager from '{}'", fileName);
        try {
            return new GenericPackager(fileName);
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
        final String channelClass = getFullChannelClassName(), packager = getPackager();
        if (channelClass == null || channelClass.isEmpty()) {
            log.error("Channel class undefined - cannot create channel");
            return null;
        }
        if (packager == null || packager.isEmpty()) {
            log.error("Packager config undefined - cannot create channel");
            return null;
        }

        Element channelDescriptor = new Element("channel")
            .setAttribute("name", name)
            .setAttribute("class", channelClass)
            .setAttribute("packager", GenericPackager.class.getName())
            .setAttribute("header", getHeader())
            .setAttribute("logger", Q2_LOGGER)
            .addContent(new Element("property")
                .setAttribute("name", "packager-config")
                .setAttribute("value", packager))
            .addContent(new Element("property")
                .setAttribute("name", "host")
                .setAttribute("value", getHost()))
            .addContent(new Element("property")
                .setAttribute("name", "port")
                .setAttribute("value", getPort()));

        if (JMeterUtils.getPropDefault(Q2_PACKAGER_LOGGING, false)) {
            channelDescriptor.addContent(new Element("property")
                    .setAttribute("name", "packager-logger")
                    .setAttribute("value", Q2_LOGGER))
                .addContent(new Element("property")
                    .setAttribute("name", "packager-realm")
                    .setAttribute("value", "packager"));
        }

        getChannelConfig().forEach(p ->
            channelDescriptor.addContent(new Element("property")
                .setAttribute("name", p.getName())
                .setAttribute("value", p.getValue()))
        );
        return channelDescriptor;
    }

    protected Element addSSLConfig(Element descriptor) {
        final String keystore = getKeystore();
        if (keystore == null || keystore.isEmpty()) return descriptor;

        // socketFactory attr vs property
        // https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/q2/iso/QServer.java#L252
        // https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/q2/iso/ChannelAdaptor.java#L467
        descriptor
            .addContent(isServer() ?
                new Element("attr")
                    .setAttribute("name", "socketFactory")
                    .addContent(GenericSSLSocketFactory.class.getName()) :
                new Element("property")
                    .setAttribute("name", "socketFactory")
                    .setAttribute("value", GenericSSLSocketFactory.class.getName()))
            .addContent(new Element("property")
                .setAttribute("name", "keystore")
                .setAttribute("value", keystore))
            .addContent(new Element("property")
                .setAttribute("name", "storepassword")
                .setAttribute("value", getStorePassword()))
            .addContent(new Element("property")
                .setAttribute("name", "keypassword")
                .setAttribute("value", getKeyPassword()));

        return descriptor;
    }

    protected Element addRequestListener(Element descriptor) {
        final String requestListener = getRequestListener();
        if (requestListener != null && !requestListener.isEmpty()) {
            descriptor.addContent(new Element("request-listener")
                .setAttribute("class", GroovyRequestListener.class.getName())
                .setAttribute("logger", Q2_LOGGER)
                .addContent(new Element("property")
                    .setAttribute("name", "source")
                    .setAttribute("value", requestListener)));
        }
        return descriptor;
    }

    protected Element getChannelAdaptorDescriptor(String key) {
        // https://github.com/jpos/jPOS/blob/v2_1_6/doc/src/asciidoc/ch08/channel_adaptor.adoc
        Element descriptor = new Element("channel-adaptor")
            .setAttribute("name", getChannelAdaptorName())
            .setAttribute("logger", Q2_LOGGER)
            .addContent(new Element("in").addContent(key+"-send"))
            .addContent(new Element("out").addContent(key+"-receive"))
            .addContent(new Element("reconnect-delay").addContent(
                    JMeterUtils.getPropDefault(CHANNEL_RECONNECT_DELAY, "10000")))
            .addContent(new Element("wait-for-workers-on-stop").addContent("yes"));
        return descriptor;
    }

    protected Element getOneShotChannelAdaptorDescriptor(String key) {
        // https://github.com/jpos/jPOS/blob/v2_1_6/doc/src/asciidoc/ch08/one_shot_channel_adaptor.adoc
        Element descriptor = new Element("qbean")
                .setAttribute("name", getChannelAdaptorName())
                .setAttribute("logger", Q2_LOGGER)
                .setAttribute("class", OneShotChannelAdaptorMK2.class.getName())
                .addContent(new Element("in").addContent(key+"-send"))
                .addContent(new Element("out").addContent(key+"-receive"));

        final String maxConnections = getMaxConnections();
        if (maxConnections != null && !maxConnections.isEmpty()) {
            descriptor.addContent(new Element("max-connections").addContent(getMaxConnections()));
        }
        return descriptor;
    }

    // Registers ChannelAdaptor <key>-channel and BaseChannel channel.<key>-channel
    protected QBeanSupport startChannelAdaptor() {
        final String key = getPropertyAsString(CONFIGKEY);
        Element channelDescriptor = getChannelDescriptor(key);
        if (channelDescriptor == null) return null;
        addSSLConfig(channelDescriptor);

        final String port = getPort(), host = getHost();
        if (host == null || host.isEmpty()) {
            log.error("Hostname undefined, cannot start ChannelAdaptor");
            return null;
        }
        if (port == null || port.isEmpty()) {
            log.error("Port undefined, cannot start ChannelAdaptor");
            return null;
        }
        Element descriptor = isReuseConnection() ? getChannelAdaptorDescriptor(key)
                : getOneShotChannelAdaptorDescriptor(key);
        descriptor.addContent(channelDescriptor);

        return deployAndStart(descriptor);
    }

    // Registers QServer <key>-server and ISOServer server.<key>-server
    protected QBeanSupport startQServer() {
        final String key = getPropertyAsString(CONFIGKEY);
        Element channelDescriptor = getChannelDescriptor(key);
        if (channelDescriptor == null) return null;

        final String port = getPort();
        if (port == null || port.isEmpty()) {
            log.error("Port undefined, cannot start QServer");
            return null;
        }
        // Build QBean deployment descriptor in memory
        // https://github.com/jpos/jPOS/blob/v2_1_6/doc/src/asciidoc/ch08/qserver.adoc
        Element descriptor = new Element("qserver")
            .setAttribute("name", getQServerName())
            .setAttribute("logger", Q2_LOGGER)
            .addContent(channelDescriptor)
            .addContent(new Element("attr")
                .setAttribute("name", "port")
                .setAttribute("type", Integer.class.getName())
                .addContent(getPort()))
            .addContent(new Element("in").addContent(key+"-send"))
            .addContent(new Element("out").addContent(key+"-receive"))
            .addContent(new Element("ready").addContent(key+".ready"));

        final String connectionSelection = getConnectionSelection();
        if (connectionSelection != null && !connectionSelection.isEmpty()) {
            descriptor.addContent(new Element("send-request")
                .addContent(ConnectionSelection.fromTag(connectionSelection).toString()));
        }
        addSSLConfig(descriptor);

        return deployAndStart(descriptor);
    }

    // Registers QMUX mux.<key>-mux and connects with <key>-receive and <key>-send Space queues
    // Would usually be called *after* startChannelAdaptor or startQServer.
    protected QBeanSupport startMux() {
        final String key = getPropertyAsString(CONFIGKEY);

        // Build QBean deployment descriptor in memory
        // (note the in/out queues need to be cross-wired):
        // https://github.com/jpos/jPOS/blob/v2_1_6/doc/src/asciidoc/ch08/qmux.adoc
        Element descriptor = new Element("qmux")
            .setAttribute("name", getMuxName())
            .setAttribute("logger", Q2_LOGGER)
            .addContent(new Element("in").addContent(key+"-receive"))
            .addContent(new Element("out").addContent(key+"-send"))
            .addContent(new Element("unhandled").addContent(key+"-unhandled"))
            .addContent(new Element("ready").addContent(key+".ready"));

        final String mtiMapping = getMtiMapping();
        if (mtiMapping != null && !mtiMapping.isEmpty()) {
            descriptor.addContent(new Element("mtimapping").addContent(mtiMapping));
        }
        getMuxKeyConfig().forEach(c -> {
            Element muxKey = new Element("key").addContent(c.getValue());
            if (c.getName() != null && !c.getName().isEmpty()) {
                muxKey.setAttribute("mti", c.getName());
            }
            descriptor.addContent(muxKey);
        });
        addRequestListener(descriptor);

        return deployAndStart(descriptor);
    }

    // Mimic Q2 deployment of a descriptor file, followed by starting the QBean,
    // https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/q2/Q2.java#L560
    // but using more accessible QFactory methods:
    protected QBeanSupport deployAndStart(Element descriptor) {
        if (log.isDebugEnabled()) {
            log.debug("Deploying {}", new XMLOutputter().outputString(descriptor));
        }
        QFactory qFactory = q2.getFactory();
        try {
            Object qbean = qFactory.instantiate(q2, descriptor);
            ObjectInstance obj = qFactory.createQBean(q2, descriptor, qbean);
            qFactory.startQBean(q2, obj.getObjectName());
            return (QBeanSupport) qbean;
        } catch (Exception e) {
            log.error("Failed to deploy {}", descriptor.getName(), e);
            return null;
        }
    }

    protected void stopChannelAdaptor() {
        stopAndUndeploy(NameRegistrar.getIfExists(getChannelAdaptorName()));
    }

    protected void stopQServer() {
        stopAndUndeploy(NameRegistrar.getIfExists(getQServerName()));
    }

    protected void stopMux() {
        try {
            stopAndUndeploy(getMux());
        } catch (NameRegistrar.NotFoundException ignoreBecauseItWasntRunning) {}
    }

    protected void stopAndUndeploy(QBeanSupport qbean) {
        if (qbean == null) return;
        QFactory qFactory = q2.getFactory();
        final String key = qbean.getName();
        try {
            log.debug("Undeploying {}", key);
            ObjectName objectName = new ObjectName(Q2.QBEAN_NAME+key);
            qFactory.destroyQBean(q2, objectName, qbean);
        } catch (Exception e) {
            log.error("Failed to undeploy {}", key, e);
        }
    }

    protected synchronized void startQ2() {
        q2 = Q2.getQ2();
        if (q2 == null) {
            log.debug("Creating Q2");
            q2 = new Q2(new String[]{
                "-d", JMeterUtils.getPropDefault(Q2_DEPLOY_DIR, Q2.DEFAULT_DEPLOY_DIR),
                "-no-scan", "-no-dynamic" // don't scan for new descriptor or jar files
            });
        }
        if (!q2.running()) {
            q2.start();
            if (!q2.ready(JMeterUtils.getPropDefault(Q2_STARTUP_TIMEOUT, 2000))) {
                log.error("Q2 startup timeout exceeded");
            }
        }
    }

    protected synchronized void stopQ2() {
        if (q2 != null && q2.running()) {
            q2.stop();
        }
    }

    public QMUX getMux() throws NameRegistrar.NotFoundException { return (QMUX)QMUX.getMUX(getMuxName()); }

    public String getMuxName() { return getConfigKey()+"-mux"; }

    public String getQServerName() { return getConfigKey()+"-server"; }

    public String getChannelAdaptorName() { return getConfigKey()+"-channel"; }

    protected boolean isServer() { return getHost() == null || getHost().isEmpty(); }

    @Override
    public void testStarted() {
        startQ2();

        if (getConfigKey() == null || getConfigKey().isEmpty()) {
            // Create a distinct key for naming this element's QBeans.
            setConfigKey(String.format("jmeter-%08x", hashCode()));
        }
        log.debug("'{}' setting up QBeans {}", getName(), getConfigKey());

        if (isServer()) {
            startQServer();
            startMux();

            long waitTime = JMeterUtils.getPropDefault(INCOMING_CONNECTION_TIMEOUT, 60000);
            long abortTime = System.currentTimeMillis()+waitTime;
            for (; waitTime > 0; waitTime = abortTime - System.currentTimeMillis()) {
                log.info("Waiting {} seconds for incoming client connection", waitTime/1000);
                ISOUtil.sleep(1000);
                try {
                    if (ISOServer.getServer(getQServerName()).getLastConnectedISOChannel() != null) break;
                } catch (NameRegistrar.NotFoundException e) {
                    log.error("ISOServer not found", e);
                    return;
                }
            }
        } else {
            startChannelAdaptor();
            startMux();
        }
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
    public void testStarted(String s) { testStarted(); }

    @Override
    public void testEnded(String s) { testEnded(); }

    // Accessors for mapping to TestBean GUI elements...
    public String getConfigKey() { return getPropertyAsString(CONFIGKEY); }
    public void setConfigKey(String configKey) { setProperty(new StringProperty(CONFIGKEY, configKey)); }

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

    public String getMaxConnections() { return getPropertyAsString(MAXCONNECTIONS); }
    public void setMaxConnections(String maxConnections) { setProperty(new StringProperty(MAXCONNECTIONS, maxConnections)); }

    public String getConnectionSelection() { return getPropertyAsString(CONNECTIONSELECTION); }
    public void setConnectionSelection(String connectionSelection) { setProperty(new StringProperty(CONNECTIONSELECTION, connectionSelection)); }

    // Need Collection getter/setter for TestBean GUI
    public Collection<ChannelConfigItem> getChannelConfig() {
        Collection<ChannelConfigItem> items = new ArrayList<>();
        JMeterProperty cfg = getProperty(CHANNELCONFIG);
        if (cfg instanceof CollectionProperty) {
            ((CollectionProperty)cfg).iterator()
                .forEachRemaining(p -> items.add((ChannelConfigItem) p.getObjectValue()));
        }
        return items;
    }

    public void setChannelConfig(Collection<ChannelConfigItem> items) {
        setProperty(new CollectionProperty(CHANNELCONFIG, items));
    }

    public String getKeystore() { return getPropertyAsString(KEYSTORE); }
    public void setKeystore(String keystore) { setProperty(new StringProperty(KEYSTORE, keystore)); }

    public String getStorePassword() { return getPropertyAsString(STOREPASSWORD); }
    public void setStorePassword(String storePassword) { setProperty(new StringProperty(STOREPASSWORD, storePassword)); }

    public String getKeyPassword() { return getPropertyAsString(KEYPASSWORD); }
    public void setKeyPassword(String keyPassword) { setProperty(new StringProperty(KEYPASSWORD, keyPassword)); }

    public boolean isReuseConnection() { return getPropertyAsBoolean(REUSECONNECTION); }
    public void setReuseConnection(boolean reuseConnection) { setProperty(new BooleanProperty(REUSECONNECTION, reuseConnection)); }

    public String getRequestListener() { return getPropertyAsString(REQUESTLISTENER); }
    public void setRequestListener(String requestListener) { setProperty(new StringProperty(REQUESTLISTENER, requestListener)); }

    public String getMtiMapping() { return getPropertyAsString(MTIMAPPING); }
    public void setMtiMapping(String mtiMapping) { setProperty(new StringProperty(MTIMAPPING, mtiMapping)); }

    // Need Collection getter/setter for TestBean GUI
    public Collection<MuxKeyConfigItem> getMuxKeyConfig() {
        Collection<MuxKeyConfigItem> items = new ArrayList<>();
        JMeterProperty cfg = getProperty(MUXKEYCONFIG);
        if (cfg instanceof CollectionProperty) {
            ((CollectionProperty)cfg).iterator()
                .forEachRemaining(p -> items.add((MuxKeyConfigItem) p.getObjectValue()));
        }
        return items;
    }

    public void setMuxKeyConfig(Collection<MuxKeyConfigItem> items) {
        setProperty(new CollectionProperty(MUXKEYCONFIG, items));
    }
}
