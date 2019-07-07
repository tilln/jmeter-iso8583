package nz.co.breakpoint.jmeter.iso8583;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;

public class MessagePrinter {

    public static String toString(ISOMsg msg, boolean hexdump) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        msg.dump(new PrintStream(baos, true), "");
        StringBuilder sb = new StringBuilder(baos.toString());
        if (hexdump && msg.getPackager() != null) {
            sb.append("\n<!--\n");
            try {
                sb.append(ISOUtil.hexdump(msg.pack()));
            } catch (ISOException e) {
                sb.append(e.toString());
            }
            sb.append("-->");
        }
        return sb.toString();
    }
}