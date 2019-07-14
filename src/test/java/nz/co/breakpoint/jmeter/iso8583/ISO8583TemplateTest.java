package nz.co.breakpoint.jmeter.iso8583;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ISO8583TemplateTest extends ISO8583TestBase {

    ISO8583Template instance = new ISO8583Template();

    @Test
    public void shouldMergeOtherTemplates() {
        instance.setFields(asMessageFields(
            new MessageField("7", "inner-only"),
            new MessageField("41", "inner")
        ));

        ISO8583Template other = new ISO8583Template();
        other.setFields(asMessageFields(
            new MessageField("11", "outer-only"),
            new MessageField("41", "outer")
        ));

        instance.merge(other);

        assertEquals(3, instance.getFields().size());
        assertTrue(instance.hasField("7"));
        assertTrue(instance.hasField("11"));
        assertTrue(instance.hasField("41"));
    }
}
