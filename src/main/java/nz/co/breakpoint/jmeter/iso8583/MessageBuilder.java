package nz.co.breakpoint.jmeter.iso8583;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.jpos.iso.*;
import org.jpos.tlv.ISOTaggedField;

/* Builds an ISOMsg from elements configured in the JMeter elements.
 */
public class MessageBuilder {

    protected ISOMsg msg;

    public MessageBuilder(ISOPackager packager) {
        init(packager, null,null);
    }

    protected void init(ISOPackager packager, ISOHeader header, byte[] trailer) {
        msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setHeader(header);
        msg.setTrailer(trailer);
    }

    public ISOMsg getMessage() {
        return msg;
    }

    public MessageBuilder header(String header) {
        if (header != null && !header.isEmpty()) {
            msg.setHeader(ISOUtil.hex2byte(header));
        }
        return this;
    }

    public MessageBuilder trailer(String trailer) {
        if (trailer != null && !trailer.isEmpty()) {
            msg.setTrailer(ISOUtil.hex2byte(trailer));
        }
        return this;
    }

    public MessageBuilder define(Iterable<MessageField> fields) throws ISOException {
        init(msg.getPackager(), msg.getISOHeader(), msg.getTrailer());
        return extend(fields);
    }

    public MessageBuilder extend(Iterable<MessageField> fields) throws ISOException {
        if (fields != null) {
            for (MessageField f : fields) {
                String id = f.getName().trim();
                if (id.isEmpty()) continue; // ignore incomplete table rows

                if (f.getTag().isEmpty()) {
                    // no tag => normal ISOField can be set directly (value will be interpreted by field class):
                    msg.set(id, f.getContent());
                } else {
                    // tag => ISOTaggedField has to be created explicitly, and needs to know the subfield Id:
                    int lastDot = id.lastIndexOf('.');
                    int subfieldId = Integer.parseInt(id.substring(lastDot+1));
                    msg.set(id, new ISOTaggedField(f.getTag(), new ISOField(subfieldId, f.getContent())));
                }
            }
        }
        return this;
    }

    // TODO extract elsewhere
    public static String getMessageAsString(ISOMsg msg, boolean hexdump) {
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
