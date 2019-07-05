package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
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
        instance = new MessageBuilder().withPackager(new GenericPackager(defaultPackagerFile));
    }

    @Test
    public void shouldAcceptEmptyFields() throws ISOException {
        ISOMsg msg = instance.define(null).msg;
        assertNotNull(msg);
        msg = instance.define(Arrays.asList()).msg;
        assertNotNull(msg);
        assertEquals(0, msg.getMaxField());
    }

    @Test
    public void shouldPopulateMTI() throws ISOException {
        fields = Arrays.asList(
            new MessageField("0", "0800")
        );
        ISOMsg msg = instance.define(fields).msg;
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
        ISOMsg msg = instance.extend(fields).msg;
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
        ISOMsg msg = instance.define(fields).msg;
        assertTrue(msg.hasFields(new int[]{0, 11}));
        assertFalse(msg.hasField("70"));
        assertEquals("0200", msg.getMTI());
        assertEquals("1234", msg.getString(11));
    }

    @Test
    public void shouldPopulateSubfields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("43.1", "JMETER"),
            new MessageField("43.2", "WELLINGTON"),
            new MessageField("43.3", "NZ")
        );
        ISOMsg msg = instance.define(fields).msg;
        assertTrue(msg.hasField(43));
        assertEquals("JMETER", msg.getString("43.1"));
        assertEquals("WELLINGTON", msg.getString("43.2"));
        assertEquals("NZ", msg.getString("43.3"));
    }

    @Test
    public void shouldPopulateTLVSubfields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("48.1", "TODO", "9C"),
            new MessageField("48.2", "1234", "9f26"),
            new MessageField("48.3", "abcdef", "9F36")
        );
        ISOMsg msg = instance.define(fields).pack().msg;
        assertEquals("303030303030303030303031303030303032309c025f5f9f2604313233349f3606616263646566",
            instance.getMessageBytes());
        assertTrue(msg.hasField(48));
        assertEquals("1234", msg.getString("48.2"));
        assertTrue(msg.getComponent("48.2") instanceof ISOTaggedField);
        assertEquals("9f26", ((ISOTaggedField)msg.getComponent("48.2")).getTag());
    }

    @Test
    public void shouldPrintMessage() throws ISOException {
        String dump = instance.define(asMessageFields(getTestMessage())).getMessageAsString(true);
        assertTrue(dump.startsWith("<isomsg>"));
    }

    @Test
    public void shouldPackMessage() throws ISOException {
        instance.define(asMessageFields(getTestMessage())).pack();
        assertTrue(instance.getMessageBytes().matches("[0-9A-F]{16,}"));
        assertTrue(instance.getMessageSize() >= 16);
    }

    @Test
    public void shouldPackBinaryFields() throws ISOException {
        fields = Arrays.asList(
            new MessageField("52", "11223344AABBCCDD")
        );
        ISOMsg msg = instance.define(fields).msg;
        assertTrue(msg.hasField(52));
        assertEquals("11223344AABBCCDD", msg.getString(52));
    }
}
