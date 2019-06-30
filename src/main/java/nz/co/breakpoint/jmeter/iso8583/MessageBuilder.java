package nz.co.breakpoint.jmeter.iso8583;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.Key;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jpos.iso.*;
import org.jpos.security.jceadapter.JCEHandler;
import org.jpos.security.jceadapter.JCEHandlerException;
import org.jpos.tlv.ISOTaggedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Builds an ISOMsg from elements configured in the JMeter elements.
 */
public class MessageBuilder {

    private static final Logger log = LoggerFactory.getLogger(ISO8583Sampler.class);

    protected ISOMsg msg = new ISOMsg();
    private byte[] packedMsg; // cache a packed version of the message to avoid repeated packing
    protected String macAlgorithm = "";
    protected Key macKey;
    protected ISOBasePackager packager; // ISOPackager has no access to field packagers
    protected JCEHandler jceHandler;

    public MessageBuilder() {
        this(null);
    }

    public MessageBuilder(ISOBasePackager packager) {
        withPackager(packager);
        try {
            jceHandler = new JCEHandler(BouncyCastleProvider.class.getName());
        } catch (JCEHandlerException e) {
            log.error("Failed to create JCEHandler", e);
        }
    }

    public ISOMsg build() throws ISOException {
        assert packager != null;
        // TODO calculate cryptograms
        calculateMAC();
        return msg;
    }

    public MessageBuilder withPackager(ISOBasePackager packager) {
        this.packager = packager;
        return this;
    }

    public MessageBuilder define(Iterable<MessageField> fields) throws ISOException {
        msg = new ISOMsg();
        assert packager != null;
        msg.setPackager(packager);
        return extend(fields);
    }

    public MessageBuilder extend(Iterable<MessageField> fields) throws ISOException {
        if (fields != null) {
            for (MessageField f : fields) {
                if (f.getTag().isEmpty()) {
                    // no tag => normal ISOField can be set directly:
                    msg.set(f.getName(), f.getContent());
                } else {
                    // tag => ISOTaggedField has to be created explicitly, and needs to know the subfield Id:
                    int lastDot = f.getName().lastIndexOf('.');
                    int subfieldId = Integer.parseInt(f.getName().substring(lastDot+1));
                    msg.set(f.getName(), new ISOTaggedField(f.getTag(), new ISOField(subfieldId, f.getContent())));
                }
            }
            packedMsg = msg.pack();
        }
        return this;
    }

    public MessageBuilder withMac(String macAlgorithm, String macKey) {
        if (macAlgorithm != null && !macAlgorithm.isEmpty()) {
            this.macAlgorithm = macAlgorithm;
        }
        if (macKey != null && !macKey.isEmpty()) {
            Key newKey = new SecretKeySpec(ISOUtil.hex2byte(macKey), this.macAlgorithm);
            if (!newKey.equals(this.macKey)) {
                /* Only assign new key if it is different, to prevent leaking MacEngineKeys in JCEHandler
                 * that only compare keys by reference:
                 * https://github.com/jpos/jPOS/blob/v2_1_3/jpos/src/main/java/org/jpos/security/jceadapter/JCEHandler.java#L442
                 */
                this.macKey = newKey;
            }
        }
        return this;
    }

    // For SampleResult
    int getMessageSize() {
        return packedMsg == null ? 0 : packedMsg.length;
    }

    // For unit tests
    String getMessageBytes() {
        return ISOUtil.byte2hex(packedMsg);
    }

    public static String getMessageAsString(ISOMsg msg) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        msg.dump(new PrintStream(baos, true), "");
        return baos.toString();
    }

    public String getMessageAsString(boolean hexdump) {
        StringBuilder sb = new StringBuilder(getMessageAsString(msg));
        if (hexdump && packedMsg != null) {
            sb.append("\n<!--\n").append(ISOUtil.hexdump(packedMsg)).append("-->");
        }
        return sb.toString();
    }

    protected void calculateMAC() throws ISOException {
        if (macKey == null || macAlgorithm.isEmpty() || jceHandler == null) {
            log.debug("Skipping MAC calculation ({})",
                macKey == null ? "no key defined" :
                macAlgorithm.isEmpty() ? "no MAC algorithm defined" :
                "no JCEHandler defined");
            return;
        }
        int macField = msg.getMaxField() <= 64 ? 64 : 128;
        int macLength = packager.getFieldPackager(macField).getLength();
        String dummyMac = String.format("%0" + 2*macLength + "d", 0);
        msg.set(macField, dummyMac);
        packedMsg = msg.pack();
        try {
            byte[] mac = jceHandler.generateMAC(Arrays.copyOf(packedMsg, packedMsg.length-macLength),
                    macKey, macAlgorithm);
            msg.set(macField, ISOUtil.padright(ISOUtil.byte2hex(mac), 2*macLength, 'F'));
            packedMsg = msg.pack();
        } catch (JCEHandlerException e) {
            log.error("MAC calculation failed", e);
        }
    }
}
