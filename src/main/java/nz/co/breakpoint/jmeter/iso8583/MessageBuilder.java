package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.util.JMeterUtils;
import org.jpos.emv.EMVStandardTagType;
import org.jpos.emv.UnknownTagNumberException;
import org.jpos.iso.*;
import org.jpos.tlv.ISOTaggedField;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583TestElement.BINARY_FIELD_TAGS;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583TestElement.DELIMITER_REGEX;

/** Builds an ISOMsg from elements configured in the JMeter script.
 * Interprets the field content as binary or non-binary depending on the packager configuration.
 * Must have an ISOBasePackager assigned to be able to find the fields' classes.
 */
public class MessageBuilder {

    protected ISOMsg msg;

    public MessageBuilder() {
        init(null, null, null);
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

    public MessageBuilder packager(ISOPackager packager) {
        msg.setPackager(packager);
        return this;
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
                final String id = f.getName().trim(), tag = f.getTag().trim();

                if (id.isEmpty()) continue; // ignore incomplete table rows

                if (tag.isEmpty()) {
                    // no tag => let ISOMsg parse the id
                    if (isBinaryField(id)) {
                        msg.set(id, ISOUtil.hex2byte(f.getContent()));
                    } else {
                        msg.set(id, f.getContent());
                    }
                } else {
                    // tag => ISOTaggedField has to be created explicitly, and needs to know the subfield Id:
                    int lastDot = id.lastIndexOf('.');
                    int subfieldId = Integer.parseInt(id.substring(lastDot+1));

                    // Make sure any binary tags' contents are interpreted correctly:
                    ISOComponent content = isBinaryField(id) || isBinaryFieldTag(tag) ?
                        new ISOBinaryField(subfieldId, ISOUtil.hex2byte(f.getContent())) :
                        new ISOField(subfieldId, f.getContent());

                    msg.set(id, new ISOTaggedField(tag, content));
                }
            }
        }
        return this;
    }

    protected boolean isBinaryFieldTag(String tag) {
        try {
            // try to find it in the standard EMV tags:
            switch (EMVStandardTagType.forHexCode(tag).getFormat()) {
                case BINARY:
                case CONSTRUCTED:
                case PROPRIETARY:
                    return true;
            }
        } catch (UnknownTagNumberException ignore) {}
        for (String bft : JMeterUtils.getPropDefault(BINARY_FIELD_TAGS, "").split(DELIMITER_REGEX)) {
            if (bft.equalsIgnoreCase(tag))
                return true;
        }
        return false;
    }

    protected boolean isBinaryField(String id) {
        return isBinaryField(id, msg.getPackager());
    }

    protected boolean isBinaryField(String id, ISOPackager packager) {
        if (!(packager instanceof ISOBasePackager)) return false; // packager unknown, use String value

        int firstDot = id.indexOf('.');
        int fieldNo = Integer.parseInt(firstDot < 0 ? id : id.substring(0, firstDot));
        String subfield = id.substring(firstDot+1);

        ISOFieldPackager fp = ((ISOBasePackager) packager).getFieldPackager(fieldNo);

        if (fp instanceof ISOMsgFieldPackager) {
            ISOMsgFieldPackager mfp = (ISOMsgFieldPackager) fp;
            if (firstDot < 0) { // no subfields given
                return mfp.getISOFieldPackager() instanceof ISOBinaryFieldPackager;
            }
            return isBinaryField(subfield, mfp.getISOMsgPackager());
        }
        // This is how ISOMsg.set() decides whether to interpret as binary:
        // https://github.com/jpos/jPOS/blob/v2_1_6/jpos/src/main/java/org/jpos/iso/ISOMsg.java#L238-L239
        return fp instanceof ISOBinaryFieldPackager;
    }
}
