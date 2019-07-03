package nz.co.breakpoint.jmeter.iso8583;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.iso.packager.XMLPackager;

public class ISO8583TestBase {
// TODO we may need some JMeter testbed later on...
//    @ClassRule
//    public static final JMeterPropertiesResource props = new JMeterPropertiesResource();
//    @ClassRule
//    public static final JMeterContextResource ctx = new JMeterContextResource();

    public static final String DEFAULT_MAC_KEY = "13131313131313131313131313131313";

    protected ISOBasePackager defaultPackager;
    protected ISOPackager xmlPackager;

    public ISO8583TestBase() {
        try {
            defaultPackager = new GenericPackager("src/test/resources/test-packager.xml");
            xmlPackager = new XMLPackager();
        } catch (ISOException e) {
            e.printStackTrace();
        }
    }

    public String isoMessageToXml(ISOMsg msg) {
        try {
            return new String(xmlPackager.pack(msg));
        } catch (ISOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ISOMsg xmlToIsoMsg(String xml) {
        ISOMsg msg = xmlPackager.createISOMsg();
        try {
            xmlPackager.unpack(msg, new ByteArrayInputStream(xml.getBytes()));
        } catch (IOException | ISOException e) {
            e.printStackTrace();
            return null;
        }
        return msg;
    }

    public static ISO8583Config getDefaultTestConfig() {
        ISO8583Config config = new ISO8583Config();
        config.setPackager("src/test/resources/test-packager.xml");
        config.setClassname(ASCIIChannel.class.getName());
        config.setHost("localhost");
        config.setPort("10000");
        config.setProperty(ISO8583Config.CONFIGKEY, "jmeter");
        return config;
    }

    public static Collection<MessageField> asMessageFields(ISOMsg msg) {
        Collection<MessageField> fields = new ArrayList<>();
        for (int i = 0; i <= msg.getMaxField(); ++i) {
            if (msg.hasField(i) && i != 1) { // don't add Bitmap
                fields.add(new MessageField(String.valueOf(i), msg.getString(i)));
            }
        }
        return fields;
    }

    public ISOMsg getTestMessage() {
        ISOMsg msg = new ISOMsg("0800");
        msg.set(11, "012345");
        msg.set(41, "543210");
        return msg;
    }
}
