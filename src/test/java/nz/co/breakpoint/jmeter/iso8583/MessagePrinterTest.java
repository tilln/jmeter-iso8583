package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.iso.ISOMsg;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessagePrinterTest extends ISO8583TestBase {

    ISOMsg msg = getDefaultTestMessage();

    @Test
    public void shouldPrintMessage() {
        String dump = MessagePrinter.asString(msg, true);
        assertTrue(dump.startsWith("<isomsg>"));
        assertTrue(dump.contains("<field id=\"0\" value=\"0800\"/>"));
        assertTrue(dump.contains("<field id=\"11\" value=\"012345\"/>"));
        assertTrue(dump.contains("<field id=\"41\" value=\"543210\"/>"));
        assertFalse(dump.contains("<!--")); // no packager, no hexdump
    }

    @Test
    public void shouldPrintMessageWIthHexDump() {
        msg.setPackager(getDefaultTestConfig().createPackager());
        String dump = MessagePrinter.asString(msg, true);
        assertTrue(dump.contains("<!--")); // no packager, no hexdump
        assertTrue(dump.contains("0000  30 38 30 30 ")); // ASCII 0800
    }
}