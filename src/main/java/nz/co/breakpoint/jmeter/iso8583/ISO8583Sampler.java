package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestElement;
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
        RCFIELD = "responseCodeField",
        RCSUCCESS = "successResponseCode";

    protected ISO8583Config config = new ISO8583Config();
    protected transient MessageBuilder builder = new MessageBuilder();

    @Override
    public boolean applies(ConfigTestElement configElement) {
        return configElement instanceof ISO8583Config;
    }

    @Override
    public void addTestElement(TestElement el) {
        if (el instanceof ISO8583Config) {
            log.debug("Applying config '{}'", el.getName());
            ISO8583Config config = (ISO8583Config) el;

            // Merge any ISO8583Config elements in scope into this sampler when traversing the JMeter test plan:
            this.config.addConfigElement(config);

            String packager = config.getPackager();
            if (packager != null && !packager.isEmpty()) {
                builder.withPackager(config.createPackager());
            }
        }
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);
        result.setRequestHeaders("Host: "+config.getHost()+"\nPort: "+config.getPort());

        ISOMsg request, response;
        try {
            request = builder.build();
        } catch (ISOException e) {
            if (log.isDebugEnabled()) {
                log.debug(ExceptionUtils.getStackTrace(e));
            } else {
                log.error(e.toString());
            }
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
            log.error("Send failed", e.toString());
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
        String rcField = getResponseCodeField();
        String rc = response.getString(rcField);
        result.setResponseCode(rc);
        String success = getSuccessResponseCode();
        result.setSuccessful( // default to true if no field or success value defined
            success == null || success.isEmpty() ||
            rcField == null || rcField.isEmpty() ||
            success.equals(rc)
        );
        result.setResponseData(builder.getMessageAsString(response), null);
        result.setResponseMessage(response.toString());

        try {
            byte[] bytes = response.pack();
            result.setBytes((long)bytes.length);
            result.setBodySize((long)bytes.length);
        } catch (ISOException e) {
            log.warn("Packaging error - failed to calculate response message size\n{}", e);
        }
        return result;
    }

    protected ISOMsg sendMessage(ISOMsg request) throws ISOException {
        MUX mux;
        try {
            mux = QMUX.getMUX(config.getMuxName());
        } catch (NameRegistrar.NotFoundException e) {
            log.error("Send failed", e);
            return null;
        }
        assert mux != null;
        return mux.request(request, getTimeout());
    }

    // For programmatic access from preprocessors...
    public ISOMsg getMessage() {
        try {
            builder.define(getFields());
        } catch (ISOException e) {
            log.error("Fields incorrect - {}", e.toString());
        }
        return builder.getMessage();
    }

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
        try {
            builder.extend(Arrays.asList(field));
        } catch (ISOException e) {
            log.error("Field incorrect - {}", e.toString());
        }
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
        try {
            builder.define(fields);
        } catch (ISOException e) {
            log.error("Fields incorrect - {}", e);
        }
    }

    public int getTimeout() { return getPropertyAsInt(TIMEOUT); }
    public void setTimeout(int timeout) { setProperty(new IntegerProperty(TIMEOUT, timeout)); }

    public String getResponseCodeField() { return getPropertyAsString(RCFIELD); }
    public void setResponseCodeField(String responseCodeField) { setProperty(RCFIELD, responseCodeField); }

    public String getSuccessResponseCode() { return getPropertyAsString(RCSUCCESS); }
    public void setSuccessResponseCode(String successResponseCode) { setProperty(RCSUCCESS, successResponseCode); }

}
