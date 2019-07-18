package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.tlv.ISOTaggedField;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

public class MessageBuilderTest extends ISO8583TestBase {
    MessageBuilder instance;
    Collection<MessageField> fields;

    @Before
    public void setup() throws ISOException {
        instance = new MessageBuilder(getDefaultTestConfig().createPackager());
    }

    @Test
    public void shouldAcceptEmptyFields() throws ISOException {
        ISOMsg msg = instance.define(null).getMessage();
        assertNotNull(msg);
        msg = instance.define(Arrays.asList()).getMessage();
        assertNotNull(msg);
        assertEquals(0, msg.getMaxField());
    }

    @Test
    public void shouldPopulateMTI() throws ISOException {
        fields = Arrays.asList(
            new MessageField("0", "0800")
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertTrue(msg.hasMTI());
        assertEquals("0800", msg.getMTI());
    }

    @Test
    public void shouldExtendMessage() throws ISOException {
        fields = Arrays.asList(
            new MessageField("0", "0800"),
            new MessageField("70", "301")
        );
        instance.define(fields);
        fields = Arrays.asList(
            new MessageField("0", "0200"),
            new MessageField("11", "1234")
        );
        ISOMsg msg = instance.extend(fields).getMessage();
        assertTrue(msg.hasMTI());
        assertTrue(msg.hasFields(new int[]{0, 11, 70}));
        assertEquals("0200", msg.getMTI());
        assertEquals("301", msg.getString(70));
        assertEquals("1234", msg.getString(11));
    }

    @Test
    public void shouldClearContentForNextMessage() throws ISOException {
        fields = Arrays.asList(
            new MessageField("0", "0800"),
            new MessageField("70", "301")
        );
        instance.define(fields);
        fields = Arrays.asList(
            new MessageField("0", "0200"),
            new MessageField("11", "1234")
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertTrue(msg.hasFields(new int[]{0, 11}));
        assertFalse(msg.hasField("70"));
        assertEquals("0200", msg.getMTI());
        assertEquals("1234", msg.getString(11));
    }

    @Test
    public void shouldRecogniseBinaryFields() {
        assertFalse(instance.isBinaryField("0"));
        assertFalse(instance.isBinaryField("39"));

        assertFalse(instance.isBinaryField("43"));
        assertFalse(instance.isBinaryField("43.1"));
        assertTrue(instance.isBinaryField("43.3"));

        assertTrue(instance.isBinaryField("55"));
        assertFalse(instance.isBinaryField("55.1"));

        assertTrue(instance.isBinaryField("60"));
        assertFalse(instance.isBinaryField("60.1"));
        assertFalse(instance.isBinaryField("60.2")); // can't recognise this as binary since the delegate is not exposed
    }

    @Test
    public void shouldPopulateSubfields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("43.1", "JMETER"),
            new MessageField("43.2", "WELLINGTON"),
            new MessageField("43.3", ISOUtil.byte2hex("NZ".getBytes()))
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertTrue(msg.hasField(43));
        assertEquals("JMETER", msg.getString("43.1"));
        assertEquals("WELLINGTON", msg.getString("43.2"));
        assertEquals("NZ", new String(msg.getBytes("43.3")));

        ISOMsg msg2 = instance.define(Arrays.asList(
            new MessageField("43", "JMETER                   WELLINGTON   NZ")
        )).getMessage();
        assertEquals(ISOUtil.byte2hex(msg.pack()), ISOUtil.byte2hex(msg2.pack()));
    }

    @Test
    public void shouldPopulateTaggedSubfields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("48.1", ISOUtil.byte2hex("cafe".getBytes()), "a1"),
            new MessageField("48.2", "1234", "a2"),
            new MessageField("48.3", "abcdef", "a3")
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertTrue(msg.hasField(48));
        assertEquals("1234", msg.getString("48.2"));
        assertTrue(msg.getComponent("48.2") instanceof ISOTaggedField);
        assertEquals("a2", ((ISOTaggedField)msg.getComponent("48.2")).getTag());
        assertEquals("0000000000010000030a1004cafea204123400a3006abcdef", new String(msg.pack()));
    }

    @Test
    public void shouldPopulateTLVSubfields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("55.1", "191119", "9a"), // string/numeric
            new MessageField("55.2", "1234567890abcdef", "9f26")// binary
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertTrue(msg.hasField(55));
        assertEquals("1234567890ABCDEF", msg.getString("55.2"));
        assertTrue(msg.getComponent("55.2") instanceof ISOTaggedField);
        assertEquals("9f26", ((ISOTaggedField)msg.getComponent("55.2")).getTag());
        assertEquals("30303030303030303030303030323030109a031911199f26081234567890abcdef", ISOUtil.byte2hex(msg.pack()));
    }

    @Test
    public void shouldPackBinaryFields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("52", "1122334455667788")
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertEquals("00000000000010001122334455667788", new String(msg.pack()));
    }

    @Test
    public void shouldPackNonBinaryFields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("11", "123456")
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertEquals("0020000000000000123456", new String(msg.pack()));
    }

    @Test
    public void shouldIgnoreWrongBinaryContent() throws ISOException {
        fields = Arrays.asList(
            new MessageField("52", "1122334455++$$ZZ")
        );
        ISOMsg msg = instance.define(fields).getMessage();
        assertEquals("00000000000010001122334455FFFFFF", new String(msg.pack()));
    }

}
