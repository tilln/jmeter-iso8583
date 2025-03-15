package nz.co.breakpoint.jmeter.iso8583;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.tlv.ISOTaggedField;
import org.junit.Before;
import org.junit.Test;
import static org.jpos.emv.EMVStandardTagType.APPLICATION_CRYPTOGRAM_0x9F26;
import static org.jpos.emv.EMVStandardTagType.ISSUER_APPLICATION_DATA_0x9F10;
import static org.junit.Assert.*;

public class ISO8583CryptoTest extends ISO8583TestBase {

    ISO8583Crypto instance = new ISO8583Crypto();
    ISO8583Sampler sampler = new ISO8583Sampler();

    static final int[] possibleMACFields = new int[]{
        ISO8583Crypto.MAC_FIELD_NO, 2*ISO8583Crypto.MAC_FIELD_NO, 3*ISO8583Crypto.MAC_FIELD_NO};
    static final Collection<MessageField> iccData = asMessageFields(
        new MessageField("55.1", "000000000001", "9F02"),
        new MessageField("55.2", "000000000000", "9F03"),
        new MessageField("55.3", "0554", "9F1A"),
        new MessageField("55.4", "0000000000", "95"),
        new MessageField("55.5", "0554", "5F2A"),
        new MessageField("55.6", "230123", "9A"),
        new MessageField("55.7", "01", "9C"),
        new MessageField("55.8", "11223344", "9F37"),
        new MessageField("55.9", "5C00", "82"),
        new MessageField("55.10", "0001", "9F36"),
        new MessageField("55.11", "06011203000000", "9F10")
    );

    @Before
    public void setup() {
        configureSampler(sampler, getDefaultTestConfig(), asMessageFields(getDefaultTestMessage()));
    }

    @Test
    public void shouldNotEncryptPINBlockWithMissingValues() {
        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey("");
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertFalse(msg.hasField(instance.PIN_FIELD_NO));

        instance.setPinField("");
        instance.setPinKey(DEFAULT_3DES_KEY);
        instance.process();
        msg = sampler.getRequest();
        assertFalse(msg.hasField(instance.PIN_FIELD_NO));

        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey(DEFAULT_3DES_KEY);
        sampler.addField(String.valueOf(instance.PIN_FIELD_NO), "");
        instance.process();
        msg = sampler.getRequest();
        assertEquals("", msg.getString(instance.PIN_FIELD_NO));
    }

    @Test
    public void shouldEncryptPINBlock() {
        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey(DEFAULT_3DES_KEY);
        String clearPinBlock = "0000000000000000";
        sampler.addField(String.valueOf(instance.PIN_FIELD_NO), clearPinBlock);
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(instance.PIN_FIELD_NO));
        String pinBlock = msg.getString(instance.PIN_FIELD_NO);
        assertEquals(clearPinBlock.length(), pinBlock.length());
        assertTrue(msg.getString(instance.PIN_FIELD_NO).matches("[0-9A-F]{16}"));
    }

    @Test
    public void shouldNotCalculateMACWithMissingValues() {
        instance.setMacKey("");
        instance.setMacAlgorithm("DESEDE");
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertFalse(msg.hasAny(possibleMACFields));

        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.setMacAlgorithm("");
        instance.process();
        msg = sampler.getRequest();
        assertFalse(msg.hasAny(possibleMACFields));
    }

    @Test
    public void shouldAllowArbitraryMACField() {
        instance.setMacAlgorithm("DESEDE");
        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.setMacField("52");
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(52));
        assertTrue(msg.getString(52).matches("[0-9A-F]{16}"));
    }

    @Test
    public void shouldCalculateMACInLastField() {
        instance.setMacAlgorithm("DESEDE");
        instance.setMacKey(DEFAULT_3DES_KEY);
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(64));
        assertTrue(msg.getString(64).matches("[0-9A-F]{16}"));
        assertEquals(msg.getMaxField(), 64);

        sampler.addField("70", "301");
        instance.process();
        msg = sampler.getRequest();
        assertTrue(msg.hasField(128));
        assertEquals(128, msg.getMaxField());

        sampler.addField("131", "HELLO");
        instance.process();
        msg = sampler.getRequest();
        assertTrue(msg.hasField(192));
        assertEquals(192, msg.getMaxField());
    }

    @Test
    public void shouldCalculateARQC() throws ISOException {
        sampler.setFields(iccData);
        instance.setImkac(DEFAULT_3DES_KEY);
        instance.setIccField("55");
        instance.process();

        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField("55.12"));
        assertTrue(msg.getString("55.12").matches("[0-9A-F]{16}"));
        assertEquals(APPLICATION_CRYPTOGRAM_0x9F26.getTagNumberHex(), ((ISOTaggedField)msg.getComponent("55.12")).getTag());
    }

    @Test
    public void shouldOverrideARQCInputTags() {
        instance.setImkac(DEFAULT_3DES_KEY);
        instance.setIccField("55");

        sampler.setFields(iccData);
        instance.setTxnData(""); // automatic extraction
        instance.process();
        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField("55.12"));
        String arqc = msg.getString("55.12");

        sampler.removeField("55.12"); // remove cryptogram field
        instance.setTxnData(iccData.stream().map(MessageField::getContent).collect(Collectors.joining())+"80"); // hex data input
        instance.process();
        msg = sampler.getRequest();
        assertTrue(msg.hasField("55.12"));
        assertEquals(arqc, msg.getString("55.12"));
    }

    @Test
    public void shouldHandleDifferentIssuersForARQCCalculation() {
        sampler.setFields(iccData);
        instance.setImkac(DEFAULT_3DES_KEY);
        instance.setIccField("55");
        instance.setPan("4111111111111111");
        instance.setPsn("01");

        Arrays.asList(
            new String[]{ "06010A03000000", "84D785136EFA29D1" }, // Visa CVN10
            new String[]{ "06011203000000", "4576E1E877E459EC" }, // Visa CVN18
            new String[]{ "0210A00000000000000000000000000000FF", "AC7FFD216E326F06" }, // MCHIP CVN16
            new String[]{ "0114020000044000DAC10000000000000000", "AB2400DF6F95530B" } // MCHIP CVN22
        ).forEach(pair -> {
            String iad = pair[0], arqc = pair[1];
            sampler.setFields(iccData.stream()
                    .map(f -> ISSUER_APPLICATION_DATA_0x9F10.getTagNumberHex().equals(f.getTag()) ?
                            new MessageField(f.getName(), iad, f.getTag()) : f)
                    .collect(Collectors.toList())
            );
            instance.process();
            assertTrue(sampler.getRequest().hasField("55.12"));
            assertEquals(arqc, sampler.getRequest().getString("55.12"));
        });
    }

    @Test
    public void shouldDUKPTEncryptPINBlock() {
        instance.setPinField(String.valueOf(instance.PIN_FIELD_NO));
        instance.setPinKey(DEFAULT_3DES_KEY);
        instance.setKsnField(String.valueOf(instance.KSN_FIELD_NO));
        String clearPinBlock = "041277cccddddeee";

        sampler.addField(String.valueOf(instance.PIN_FIELD_NO), clearPinBlock);
        sampler.addField(String.valueOf(instance.KSN_FIELD_NO), "9876543210E00001");
        instance.process();

        ISOMsg msg = sampler.getRequest();
        assertTrue(msg.hasField(instance.PIN_FIELD_NO));
        String pinBlock = msg.getString(instance.PIN_FIELD_NO);
        assertEquals(clearPinBlock.length(), pinBlock.length());
        assertEquals("C0F224AD1647A8EB", pinBlock);
    }
}
