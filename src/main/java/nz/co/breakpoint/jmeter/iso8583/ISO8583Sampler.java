package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.util.Collection;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testbeans.TestBeanHelper;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.*;
import org.jpos.iso.*;
import org.jpos.q2.iso.QMUX;
import org.jpos.util.NameRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Sends an ISOMsg to a jPOS QMUX provided by an ISO8583Config element and receives a response.
 * Message fields can be specified in the associated TestBean GUI, or via ISO8583Template config elements
 * in scope. Preprocessors may also modify fields.
 */
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

    // These can't be TestElementProperties as they would be saved in the Test Plan:
    protected ISO8583Config config = new ISO8583Config();
    protected ISO8583Template template = new ISO8583Template();

    protected transient MessageBuilder builder; // reusable between samples (with same packager)
    protected transient ISOMsg response; // for PostProcessors

    private transient boolean fieldsSet = false; // indicates if the fields have been set yet to avoid duplication

    @Override
    public boolean applies(ConfigTestElement configElement) {
        return configElement instanceof ISO8583TestElement;
    }

    @Override
    public void addTestElement(TestElement el) {
        if (el instanceof ISO8583Config) {
            log.debug("Applying config '{}'", el.getName());
            // Merge any ISO8583Config elements in scope into this sampler when traversing the JMeter test plan:
            config.addConfigElement((ISO8583Config) el);
            // Make sure all messages have a packager available (to interpret String values correctly):
            builder = new MessageBuilder(config.createPackager());
        } else if (el instanceof ISO8583Template) {
            log.debug("Applying template '{}'", el.getName());
            // Add (new) message fields from any ISO8583Template elements in scope to this sampler's fields:
            template.merge((ISO8583Template) el);
        } else {
            super.addTestElement(el);
        }
    }

    // Without preparing the sampler before Preprocessors run they wouldn't see the
    // sampler's own template fields.
    // This gets called first thing in TestCompiler.configureWithConfigElements,
    // so we can sneak in the sampler's own fields to be applied (as a pseudo-config element)
    // before the surrounding ISO8583Template elements.
    // TODO perhaps even build the ISOMsg here so Preprocessors can modify that instead?
    // Though that would mean updating the ISOMsg object when modifying the template fields (as JMeterProperties)
    // or the ISOMsg must not be built again when calling getRequest() in sample()
    // otherwise the Preprocessors modifications are lost.
    @Override
    public void clearTestElementChildren() {
        TestBeanHelper.prepare(this);
        fieldsSet = true; // avoid setting fields again after Preprocessors (or their modifications may be overwritten)
    }

    // Sampler has to keep track of running vs. non-running versions for non-JMeterProperty members,
    // or else any modifications (by Preprocessors) would accumulate with each iteration.
    @Override
    public void setRunningVersion(boolean runningVersion) {
        super.setRunningVersion(runningVersion);
        config.setRunningVersion(runningVersion);
        template.setRunningVersion(runningVersion);
    }

    @Override
    public void recoverRunningVersion() {
        super.recoverRunningVersion();
        config.recoverRunningVersion();
        template.recoverRunningVersion();
        fieldsSet = false; // make sure fields get re-applied next time the sampler gets prepared
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
            response = sendRequest(request);
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
            log.error("Packager error on request '{}'. Check config! {}", getName(), e.toString());
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
            log.error("Packager error on response '{}'. Check config! {}", getName(), e.toString());
        }
        return result;
    }

    protected ISOMsg sendRequest(ISOMsg request) throws ISOException, NameRegistrar.NotFoundException {
        MUX mux = QMUX.getMUX(config.getMuxName());
        return mux.request(request, getTimeout());
    }

    // For programmatic access from Pre-/PostProcessors...
    public ISOMsg getRequest() {
        if (builder == null) { // TODO better exception handling
            log.warn("Invalid/missing ISO8583 Config for '{}'", getName());
            return new ISOMsg();
        }
        try {
            builder.define(getFields());
        } catch (ISOException e) {
            log.error("Fields incorrect - {}", e.toString());
        }
        return builder.header(getHeader()).trailer(getTrailer()).getMessage();
    }

    public ISOMsg getResponse() { return response; }

    public void addField(String id, String value) { addField(id, value, ""); }
    public void addField(String id, String value, String tag) { template.addField(new MessageField(id, value, tag)); }

    public String getHeader() { return getPropertyAsString(HEADER); }
    public void setHeader(String header) { setProperty(HEADER, header); }

    public String getTrailer() { return getPropertyAsString(TRAILER); }
    public void setTrailer(String trailer) { setProperty(TRAILER, trailer); }

    public Collection<MessageField> getFields() { return template.getFields(); }
    public void setFields(Collection<MessageField> fields) { if (!fieldsSet) template.setFields(fields); }

    public int getTimeout() { return getPropertyAsInt(TIMEOUT); }
    public void setTimeout(int timeout) { setProperty(new IntegerProperty(TIMEOUT, timeout)); }

    public String getResponseCodeField() { return getPropertyAsString(RCFIELD); }
    public void setResponseCodeField(String responseCodeField) { setProperty(RCFIELD, responseCodeField); }

    public String getSuccessResponseCode() { return getPropertyAsString(RCSUCCESS); }
    public void setSuccessResponseCode(String successResponseCode) { setProperty(RCSUCCESS, successResponseCode); }
}
