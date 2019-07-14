package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Crypto.*;

public class ISO8583CryptoBeanInfo extends ISO8583TestElementBeanInfo {
    public ISO8583CryptoBeanInfo() {
        super(ISO8583Crypto.class);

        PropertyDescriptor p;

        createPropertyGroup("PIN", new String[]{
             PINFIELD, PINKEY,
        });

        p = property(PINFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, String.valueOf(ISO8583TestElement.PIN_FIELD_NO));

        p = property(PINKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        createPropertyGroup("MAC", new String[]{
             MACALGORITHM, MACKEY,
        });

        p = property(MACALGORITHM);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        p.setValue(TAGS, macAlgorithms);

        p = property(MACKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        createPropertyGroup("ARQC", new String[]{
            ARQCFIELD, IMKAC, SKDM,
        });

        p = property(ARQCFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(IMKAC);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(SKDM);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        p.setValue(TAGS, skdMethods);

    }
}
