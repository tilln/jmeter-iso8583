package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;

import static nz.co.breakpoint.jmeter.iso8583.ISO8583Crypto.*;

public class ISO8583CryptoBeanInfo extends ISO8583TestElementBeanInfo {
    public ISO8583CryptoBeanInfo() {
        super(ISO8583Crypto.class);

        createPropertyGroup("Cryptograms", new String[]{
            PINBLOCK, PINKEY, PINFIELD, MACALGORITHM, MACKEY,
        });
        PropertyDescriptor p;

        p = property(PINBLOCK);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(PINKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(PINFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, String.valueOf(DEFAULT_PIN_FIELD));

        p = property(MACALGORITHM);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(NOT_OTHER, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        p.setValue(TAGS, ISO8583Config.macAlgorithms);

        p = property(MACKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
    }
}
