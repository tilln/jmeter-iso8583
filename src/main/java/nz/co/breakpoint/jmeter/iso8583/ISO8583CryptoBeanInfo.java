package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Crypto.*;

/** Describes the ISO8583Crypto Preprocessor GUI.
 */
public class ISO8583CryptoBeanInfo extends ISO8583TestElementBeanInfo {
    public ISO8583CryptoBeanInfo() {
        super(ISO8583Crypto.class);

        PropertyDescriptor p;

        createPropertyGroup("PIN", new String[]{
             PINFIELD, PINKEY, KSNFIELD,
        });

        p = property(PINFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, String.valueOf(ISO8583TestElement.PIN_FIELD_NO));

        p = property(PINKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(KSNFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, String.valueOf(ISO8583TestElement.KSN_FIELD_NO));

        createPropertyGroup("MAC", new String[]{
             MACALGORITHM, MACKEY, MACFIELD,
        });

        p = property(MACALGORITHM);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        p.setValue(TAGS, macAlgorithms);

        p = property(MACKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(MACFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        createPropertyGroup("ARQC", new String[]{
            ICCFIELD, IMKAC, SKDM, PAN, PSN, TXNDATA, PADDING,
        });

        p = property(ICCFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, String.valueOf(ICC_FIELD_NO));

        p = property(IMKAC);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(SKDM);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        p.setValue(TAGS, skdMethods);

        p = property(PAN);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(PSN);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "00");

        p = property(TXNDATA);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(PADDING);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
    }
}
