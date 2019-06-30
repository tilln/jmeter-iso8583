package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.jpos.q2.QBean;
import org.jpos.q2.iso.ChannelAdaptor;
import org.jpos.q2.iso.QMUX;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

public class ISO8583SamplerTest extends ISO8583TestBase {

    ISO8583Sampler instance = new ISO8583Sampler();

    @Test
    public void shouldMergeConfig() {
        ISO8583Config inner = new ISO8583Config();
        inner.setPackager("PACKAGER");
        inner.setHost("HOST");
        ISO8583Config outer = new ISO8583Config();
        outer.setHost("NOT_THIS");
        outer.setPort("PORT");
        instance.addTestElement(inner);
        instance.addTestElement(outer);
        // if only inner is defined
        assertEquals(inner.getPackager(), instance.config.getPackager());
        // if inner is defined, it has preference over outer
        assertEquals(inner.getHost(), instance.config.getHost());
        // if inner is undefined, outer should be applied
        assertEquals(outer.getPort(), instance.config.getPort());
        // if none are defined, default should be applied
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
