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

/** Sends an ISOMsg to a jPOS QMUX provided by an {@link ISO8583Config} element, and receives a response.<br />
 * Message fields can be specified in the associated TestBean GUI, or via {@link ISO8583Component} config elements
 * in scope. Preprocessors may also modify fields.<br />
 * Sampler lifecycle:
 * <pre>
    JMeterThread.executeSamplePackage
        TestCompiler.configureSampler
            TestCompiler.configureWithConfigElements
                this.clearTestElementChildren
                    TestBeanHelper.prepare : set all Bean properties
                        this.setFields : fields from sampler
                        this.setHeader
                this.addTestElement
                    this.component.merge : fields from other component(s)
                        this.component.addField
        JMeterThread.runPreProcessors
            ISO8583Crypto.process
                this.getRequest
                this.addField
        JMeterThread.delay
        JMeterThread.doSampling
            TestBeanHelper.prepare : avoid overwriting fields
                this.setFields
                this.setHeader
            this.sample
                this.getRequest
 </pre>
 * The ISOMsg gets built in {@link #getRequest()} whenever a Preprocessor needs to access the message.
 * This means it will be rebuilt from Properties, therefore Preprocessor modifications of the message itself won't
 * persist. Instead, the sampler's properties need to be modified.
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
    protected ISO8583Component component = new ISO8583Component();

    protected transient MessageBuilder builder = new MessageBuilder(); // reusable between samples
    protected transient ISOMsg response; // for PostProcessors

    private transient boolean prepared = false; // indicates if the fields have been set yet to avoid duplication

    @Override
    public boolean applies(ConfigTestElement configElement) {
        return configElement instanceof ISO8583TestElement;
    }

    @Override
    public void addTestElement(TestElement el) {
        if (el instanceof ISO8583Config) {
            log.debug("Applying config '{}'", el.getName());
            /* Merge ISO8583Config in scope into this sampler when traversing the JMeter test plan.
             * Merging multiple config elements is not supported as they would be applied outside-in
             * and each register their own QBeans.
             */
            config.addConfigElement((ISO8583Config) el);
            // Make sure all messages have a packager available (to interpret String values correctly):
            builder.packager(config.createPackager());
        } else if (el instanceof ISO8583Component) {
            log.debug("Applying component '{}'", el.getName());
            // Add (new) message fields from any ISO8583Component elements in scope to this sampler's fields:
            component.merge((ISO8583Component) el);
        } else {
            super.addTestElement(el);
        }
    }

    // Without preparing the sampler before Preprocessors run they wouldn't see the
    // sampler's own component fields.
    // This gets called first thing in TestCompiler.configureWithConfigElements,
    // so we can sneak in the sampler's own fields to be applied (as a pseudo-config element)
    // before the surrounding ISO8583Component elements.
    @Override
    public void clearTestElementChildren() {
        TestBeanHelper.prepare(this); // calls all property setters
        prepared = true; // avoid setting fields again after Preprocessors (or their modifications may be overwritten)
    }

    // Sampler has to keep track of running vs. non-running versions for non-JMeterProperty members,
    // or else any modifications (by Preprocessors) would accumulate with each iteration.
    @Override
    public void setRunningVersion(boolean runningVersion) {
        super.setRunningVersion(runningVersion);
        config.setRunningVersion(runningVersion);
        component.setRunningVersion(runningVersion);
    }

    @Override
    public void recoverRunningVersion() {
        super.recoverRunningVersion();
        config.recoverRunningVersion();
        component.recoverRunningVersion();
        prepared = false; // make sure fields get re-applied next time the sampler gets prepared
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
            byte[] bytes = request.pack(), header = request.getHeader(), trailer = request.getTrailer();
            log.debug("Packed request '{}'", ISOUtil.byte2hex(bytes));
            result.setSentBytes((long) bytes.length +
                (header != null ? header.length : 0) + (trailer != null ? trailer.length : 0));
        } catch (ISOException e) {
            log.error("Packager error on request '{}'. Check config! {}", getName(), e.toString());
        }

        // Response validation...
        if (response == null) {
            if (getTimeout() >= 0) {
                result.setResponseMessage("Timeout");
            } else { // fire-and-forget
                result.setResponseMessage("No response");
                result.setSuccessful(true);
            }
            return result;
        }
        result.setSuccessful(true); // at least we received a response, so start off as success
        result.setResponseMessageOK();

        String rcField = getResponseCodeField();
        if (rcField != null && !rcField.isEmpty()) {
            String rc = response.getString(rcField);
            result.setResponseCode(rc);

            String success = getSuccessResponseCode();
            if (success != null && !success.isEmpty()) {
                if (!success.equals(rc)) {
                    result.setSuccessful(false);
                    result.setResponseMessage("Unexpected response code");
                }
            }
        }

        // Response details...
        result.setResponseData(MessagePrinter.asString(response, true), null);
        try {
            byte[] bytes = response.pack(), header = response.getHeader(), trailer = response.getTrailer();
            log.debug("Packed response '{}'", ISOUtil.byte2hex(bytes));
            result.setHeadersSize((header != null ? header.length : 0) + (trailer != null ? trailer.length : 0));
            result.setBodySize((long) bytes.length);
        } catch (ISOException e) {
            log.error("Packager error on response '{}'. Check config! {}", getName(), e.toString());
        }
        return result;
    }

    protected ISOMsg sendRequest(ISOMsg request) throws ISOException, NameRegistrar.NotFoundException {
        MUX mux = QMUX.getMUX(config.getMuxName());
        if (getTimeout() >= 0) {
            return mux.request(request, getTimeout());
        } else {
            mux.request(request, 0, (response, handback) -> {}, null); // fire-and-forget
            return null;
        }
    }

    protected ISOMsg buildRequest() {
        try {
            builder.define(getFields());
        } catch (ISOException e) {
            log.error("Fields incorrect - {}", e.toString());
        }
        return builder.header(getHeader()).trailer(getTrailer()).getMessage();
    }

    // For programmatic access from Pre-/PostProcessors...
    public ISOMsg getRequest() { return buildRequest(); }
    public ISOMsg getResponse() { return response; }

    public void addField(String id, String value) { addField(id, value, ""); }
    public void addField(String id, String value, String tag) { component.addField(new MessageField(id, value, tag)); }

    public String getHeader() { return getPropertyAsString(HEADER); }
    public void setHeader(String header) { if (!prepared) setProperty(HEADER, header); }

    public String getTrailer() { return getPropertyAsString(TRAILER); }
    public void setTrailer(String trailer) { if (!prepared) setProperty(TRAILER, trailer); }

    public Collection<MessageField> getFields() { return component.getFields(); }
    public void setFields(Collection<MessageField> fields) { if (!prepared) component.setFields(fields); }

    public int getTimeout() { return getPropertyAsInt(TIMEOUT); }
    public void setTimeout(int timeout) { setProperty(new IntegerProperty(TIMEOUT, timeout)); }

    public String getResponseCodeField() { return getPropertyAsString(RCFIELD); }
    public void setResponseCodeField(String responseCodeField) { setProperty(RCFIELD, responseCodeField); }

    public String getSuccessResponseCode() { return getPropertyAsString(RCSUCCESS); }
    public void setSuccessResponseCode(String successResponseCode) { setProperty(RCSUCCESS, successResponseCode); }
}
