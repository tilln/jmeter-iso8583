package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.junit.rules.ExternalResource;

/* Initialises JMeter engine internals for unit tests.
 */
public class JMeterContextResource extends ExternalResource {
    JMeterContext context;

    protected void before() {
        context = JMeterContextService.getContext();
        context.setVariables(new JMeterVariables());
    }
}