package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
        HEADER = "header",
        TRAILER = "trailer",
        FIELDS = "fields",
        TIMEOUT = "timeout",
        RCFIELD = "responseCodeField",
        RCSUCCESS = "successResponseCode";

    protected ISO8583Config config = new ISO8583Config();
    protected transient MessageBuilder builder;
    protected transient ISOMsg response;

    @Override
    public boolean applies(ConfigTestElement configElement) {
        return configElement instanceof ISO8583Config;
    }

    @Override
    public void addTestElement(TestElement el) {
        if (el instanceof ISO8583Config) {
            log.debug("Applying config '{}'", el.getName());
            // Merge any ISO8583Config elements in scope into this sampler when traversing the JMeter test plan:
            config.addConfigElement((ISO8583Config) el);
            // Make sure all messages have a packager available (to interpret String values correctly):
            builder = new MessageBuilder(config.createPackager());
        }
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        ISOMsg request = getRequest();

        // Send the request...
        log.debug("sampleStart");
        result.sampleStart();
        try {
            response = sendMessage(request);
        } catch (ISOException | NameRegistrar.NotFoundException e) {
            log.error("Send failed {}", e.toString(), e);
            result.setResponseMessage(e.toString());
            return result;
        } finally {
            log.debug("sampleEnd");
            result.sampleEnd();
        }

        // Request details...
        result.setRequestHeaders("Host: "+config.getHost()+"\nPort: "+config.getPort());
        result.setSamplerData(MessagePrinter.asString(request,true));
        try {
            byte[] bytes = request.pack();
            log.debug("Packed request '{}'", ISOUtil.byte2hex(bytes));
            result.setSentBytes((long) bytes.length);
        } catch (Exception e) {
            // must be config error, e.g. Channel not running, so would have been thrown on sending above
            log.warn("Packager error on request. Check config! {}", e.toString(), e);
        }

        // Response validation...
        if (response == null) {
            result.setResponseMessage("Timeout");
            return result;
        }
        result.setSuccessful(true); // at least we received a response, so start off as success

        String rcField = getResponseCodeField();
        if (rcField != null && !rcField.isEmpty()) {
            String rc = response.getString(rcField);
            result.setResponseCode(rc);

            String success = getSuccessResponseCode();
            if (success != null && !success.isEmpty()) {
                result.setSuccessful(success.equals(rc));
            }
        }

        // Response details...
        result.setResponseData(MessagePrinter.asString(response, true), null);
        result.setResponseMessage(response.toString());

        try {
            byte[] bytes = response.pack();
            log.debug("Packed response '{}'", ISOUtil.byte2hex(bytes));
            result.setBytes((long) bytes.length);
            result.setBodySize((long) bytes.length);
        } catch (ISOException e) {
            log.warn("Packager error on response. Check config! {}", e.toString(), e);
        }
        return result;
    }

    protected ISOMsg sendMessage(ISOMsg request) throws ISOException, NameRegistrar.NotFoundException {
        MUX mux = QMUX.getMUX(config.getMuxName());
        return mux.request(request, getTimeout());
    }

    // For programmatic access from Pre-/PostProcessors...
    public ISOMsg getRequest() {
        try {
            builder.define(getFields());
        } catch (ISOException e) {
            log.error("Fields incorrect - {}", e.toString());
        }
        return builder.getMessage();
    }

    public ISOMsg getResponse() {
        return response;
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

    public String getHeader() { return getPropertyAsString(HEADER); }
    public void setHeader(String header) {
        setProperty(HEADER, header);
        builder.header(header);
    }

    public String getTrailer() { return getPropertyAsString(TRAILER); }
    public void setTrailer(String trailer) {
        setProperty(TRAILER, trailer);
        builder.header(trailer);
    }

    // Need Collection getter/setter for TestBean GUI
    public Collection<MessageField> getFields() {
        Collection<MessageField> fields = new ArrayList<>();
        JMeterProperty cfg = getProperty(FIELDS);
        if (cfg instanceof CollectionProperty) {
            ((CollectionProperty)cfg).iterator()
                .forEachRemaining(p -> fields.add((MessageField) p.getObjectValue()));
        }
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
