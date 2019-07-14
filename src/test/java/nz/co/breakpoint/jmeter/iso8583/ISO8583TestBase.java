package nz.co.breakpoint.jmeter.iso8583;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.XMLPackager;
import org.junit.ClassRule;

public class ISO8583TestBase {
    @ClassRule
    public static final JMeterPropertiesResource props = new JMeterPropertiesResource();
    @ClassRule
    public static final JMeterContextResource ctx = new JMeterContextResource();

    public static final String DEFAULT_DES_KEY = "1313131313131313";
    public static final String DEFAULT_3DES_KEY = "13131313131313131313131313131313";

    protected String defaultPackagerFile = "src/test/resources/test-packager.xml";
    protected ISOPackager xmlPackager;

    public ISO8583TestBase() {
        try {
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
        config.setProperty(ISO8583Config.CONFIG_KEY, "jmeter");
        return config;
    }

    public static ISO8583Template getDefaultTestTemplate() {
        ISO8583Template template = new ISO8583Template();
        template.addField(new MessageField("11", "STAN"));
        return template;
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

    public static Collection<MessageField> asMessageFields(MessageField... fields) {
        Collection<MessageField> collection = new ArrayList<>();
        for (MessageField f : fields) collection.add(f);
        return collection;
    }

    public ISOMsg getDefaultTestMessage() {
        ISOMsg msg = new ISOMsg("0800");
        msg.set(11, "012345");
        msg.set(41, "543210");
        return msg;
    }
}
