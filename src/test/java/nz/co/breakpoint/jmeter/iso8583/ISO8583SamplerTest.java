package nz.co.breakpoint.jmeter.iso8583;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.jmeter.control.GenericController;
import org.apache.jmeter.threads.TestCompiler;
import org.apache.jorphan.collections.ListedHashTree;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ISO8583SamplerTest extends ISO8583TestBase {

    ISO8583Sampler instance = new ISO8583Sampler();

    @Before
    public void setup() {
        configureSampler(instance);
    }

    @Test
    public void shouldApplyClosestConfig() {
        ISO8583Config inner = new ISO8583Config();
        inner.setHost("THIS");
        inner.setPackager(defaultPackagerFile);
        ISO8583Config outer = new ISO8583Config();
        outer.setHost("NOT_THIS");
        outer.setPort("PORT");

        ListedHashTree tree = new ListedHashTree();
        tree.add(outer);
        tree.add(new GenericController(), Arrays.asList(inner, instance));
        TestCompiler compiler = new TestCompiler(tree);
        tree.traverse(compiler);
        compiler.configureSampler(instance);

        // apply inner config, without merging or copying outer parts:
        assertEquals(inner.getPackager(), instance.config.getPackager());
        assertEquals(inner.getHost(), instance.config.getHost());
        assertEquals(inner.getPort(), instance.config.getPort());
        assertEquals("", instance.config.getClassname());
    }

    @Test
    public void shouldApplyExplicitConfig() {
        ISO8583Config a = new ISO8583Config();
        a.setHost("A");
        a.setConfigKey("A");
        ISO8583Config b = new ISO8583Config();
        b.setHost("B");
        b.setConfigKey("B");

        instance.setConfigKey("B");

        ListedHashTree tree = new ListedHashTree();
        tree.add(new GenericController(), Arrays.asList(a, b, instance));
        TestCompiler compiler = new TestCompiler(tree);
        tree.traverse(compiler);
        compiler.configureSampler(instance);

        assertEquals(b.getHost(), instance.config.getHost());
    }

    @Test
    public void shouldRestoreFieldsBetweenIterations() {
        instance.addTestElement(getDefaultTestConfig());
        instance.setFields(new ArrayList<>());
        instance.addField("0", "0800");
        assertEquals(1, instance.getFields().size());
        instance.setRunningVersion(true);
        instance.addField("11", "1234");
        instance.recoverRunningVersion();
        assertEquals(1, instance.getFields().size());
    }

    @Test
    public void shouldMergeNestedComponents() {
        instance.addTestElement(getDefaultTestConfig());
        instance.addField("0", "0800");
        instance.addField("11", "ALREADY_THERE");

        ISO8583Component inner = new ISO8583Component();
        inner.setFields(asMessageFields(
            new MessageField("0", "0000"),
            new MessageField("41", "THIS")
        ));
        ISO8583Component outer = new ISO8583Component();
        outer.setFields(asMessageFields(
            new MessageField("11", "IGNORED"),
            new MessageField("41", "NOT_THIS")
        ));
        instance.addTestElement(inner);
        instance.addTestElement(outer);

        assertEquals(3, instance.getFields().size());
        assertTrue(instance.getRequest().hasFields(new int[]{0, 11, 41}));
    }
}
