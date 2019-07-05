package nz.co.breakpoint.jmeter.iso8583;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class ISO8583SamplerTest extends ISO8583TestBase {

    ISO8583Sampler instance = new ISO8583Sampler();

    @Test
    public void shouldApplyClosestConfig() {
        ISO8583Config inner = new ISO8583Config();
        inner.setHost("NOT_THIS");
        ISO8583Config outer = new ISO8583Config();
        inner.setPackager(defaultPackagerFile);
        outer.setHost("THIS");
        outer.setPort("PORT");
        instance.addTestElement(inner);
        instance.addTestElement(outer);

        assertEquals(outer.getPackager(), instance.config.getPackager());
        assertEquals(outer.getHost(), instance.config.getHost());
        assertEquals(outer.getPort(), instance.config.getPort());
        assertEquals("", instance.config.getClassname());
    }

    @Test
    public void shouldRestoreFieldsBetweenIterations() {
        instance.setFields(new ArrayList<>());
        instance.addField("0", "0800");
        assertEquals(1, instance.getFields().size());
        instance.setRunningVersion(true);
        instance.addField("11", "1234");
        instance.recoverRunningVersion();
        assertEquals(1, instance.getFields().size());
    }
}
