package nz.co.breakpoint.jmeter.iso8583;

import java.io.File;
import java.io.IOException;
import org.apache.jmeter.util.JMeterUtils;
import org.junit.rules.ExternalResource;

/* Shared dummy JMeter properties file which JMeter needs for its initialisation.
 */
public class JMeterPropertiesResource extends ExternalResource {
    protected void before() {
        try {
            File props = File.createTempFile("jmeter-iso8583-test", ".properties");
            props.deleteOnExit();
            JMeterUtils.loadJMeterProperties(props.getAbsolutePath());
            JMeterUtils.initLocale();
        }
        catch (IOException e) {
            System.out.println("Failed to create JMeter properties file: "+e.getMessage());
        }
    }
}