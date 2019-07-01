package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.*;
import org.jpos.iso.*;
import org.jpos.q2.iso.QMUX;
import org.jpos.util.NameRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ISO8583Sampler extends AbstractSampler
        implements ISO8583TestElement, TestBean, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ISO8583Sampler.class);

    // JMeter Property names (appear in script files, so don't change):
    public static final String
        TIMEOUT = "timeout",
        FIELDS = "fields",
        MACALGORITHM = "macAlgorithm",
        MACKEY = "macKey",
        PINBLOCK = "pinBlock",
        PINKEY = "pinKey";

    protected ISO8583Config config = new ISO8583Config();
    protected transient MessageBuilder builder = new MessageBuilder();
    private transient MUX mux;
    private transient ISOBasePackager packager;

    @Override
    public boolean applies(ConfigTestElement configElement) {
        return configElement instanceof ISO8583Config;
    }

    // Merges any ISO8583Config element in scope into this sampler when traversing the JMeter test plan
    @Override
    public void addTestElement(TestElement el) {
        if (el instanceof ISO8583Config) {
            log.debug("Adding "+el.getName());
            config.addConfigElement((ISO8583Config)el);
        }
    }

    // Creates a cached packager instance once for each thread
    // TODO this means the packager config cannot be variable, but is this required?
    protected ISOBasePackager getPackager() {
        if (packager == null) {
            packager = config.createPackager();
            // TODO create new packager if config changes
        }
        return packager;
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);
        result.setRequestHeaders("Host: "+config.getHost()+"\nPort: "+config.getPort());

        ISOMsg request, response;
        try {
            request = builder.withPackager(getPackager())
                .define(getFields())
                .withPin(getPinBlock(), getPinKey())
                .withMac(getMacAlgorithm(), getMacKey())
                .build();
        } catch (ISOException e) {
            log.error("Error packing request", e.getNested()); // TODO unpack nested exception
            result.setResponseMessage(e.toString());
            return result;
        }
        result.setSamplerData(builder.getMessageAsString(true));
        result.setSentBytes(builder.getMessageSize());

        result.sampleStart();
        log.debug("sampleStart");
        try {
            response = sendMessage(request);
        } catch (ISOException e) {
            log.error("ISOException", e.getNested());
            result.setResponseMessage(e.toString());
            return result;
        } finally {
            log.debug("sampleEnd");
            result.sampleEnd();
        }
        if (response == null) {
            result.setResponseMessage("Timeout");
            return result;
        }
        String rc = response.getString(39);
        result.setResponseCode(rc);
        result.setSuccessful("00".equals(rc));
        result.setResponseData(builder.getMessageAsString(response), null);
        result.setResponseMessage(response.toString());
        try {
            result.setBytes((long)response.pack().length);
        } catch (ISOException e) {
            log.warn("Packaging error - failed to calculate response message size", e.getNested());
        }
        return result;
    }

    protected ISOMsg sendMessage(ISOMsg request) throws ISOException {
        try {
            mux = QMUX.getMUX("jmeter-mux"); // TODO there may be several muxes (via different configs) so need distinct names
        } catch (NameRegistrar.NotFoundException e) {
            e.printStackTrace();
            return null;
        }
        assert mux != null;
        return mux.request(request, getTimeout());
    }

    // For programmatic access from preprocessors...
    public void addField(String id, String value) {
        addField(id, value, "");
    }

    public void addField(String id, String value, String tag) {
        addField(new MessageField(id, value, tag));
    }

    protected void addField(MessageField field) {
        log.debug("Add field {}", field);
        CollectionProperty fields = (CollectionProperty)getProperty(FIELDS);
        JMeterProperty prop = AbstractProperty.createProperty(field);
        if (isRunningVersion()) {
            this.setTemporary(prop);
        }
        fields.addItem(prop);
    }

    protected void addFields(Collection<MessageField> fields) {
        fields.forEach(f -> addField(f));
    }

    // Need Collection getter/setter for TestBean GUI
    public Collection<MessageField> getFields() {
        Collection<MessageField> fields = new ArrayList<>();
        ((CollectionProperty) getProperty(FIELDS)).iterator()
            .forEachRemaining(p -> fields.add((MessageField)p.getObjectValue()));
        return fields;
    }

    public void setFields(Collection<MessageField> fields) {
        setProperty(new CollectionProperty(FIELDS, fields));
    }

    public String getMacAlgorithm() { return getPropertyAsString(MACALGORITHM); }
    public void setMacAlgorithm(String macAlgorithm) { setProperty(MACALGORITHM, macAlgorithm); }

    public String getMacKey() { return getPropertyAsString(MACKEY); }
    public void setMacKey(String macKey) { setProperty(MACKEY, macKey); }

    public String getPinBlock() { return getPropertyAsString(PINBLOCK); }
    public void setPinBlock(String pinBlock) { setProperty(PINBLOCK, pinBlock); }

    public String getPinKey() { return getPropertyAsString(PINKEY); }
    public void setPinKey(String pinKey) { setProperty(PINKEY, pinKey); }

    public int getTimeout() { return getPropertyAsInt(TIMEOUT); }
    public void setTimeout(int timeout) {
        setProperty(new IntegerProperty(TIMEOUT, timeout));
    }
}
