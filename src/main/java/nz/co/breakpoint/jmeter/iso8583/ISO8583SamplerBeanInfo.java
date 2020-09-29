package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testbeans.gui.IntegerPropertyEditor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Sampler.*;
import java.beans.PropertyDescriptor;

/** Describes the ISO8583Sampler GUI.
 */
public class ISO8583SamplerBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583SamplerBeanInfo() {
        super(ISO8583Sampler.class);

        PropertyDescriptor p;

        createPropertyGroup("Connection", new String[]{
            CONFIGKEY,
        });
        p = property(CONFIGKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        createPropertyGroup("Request", new String[]{
            HEADER, TRAILER, FIELDS,
        });
        p = property(HEADER);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(TRAILER);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        createMessageFieldsTableProperty(FIELDS);

        createPropertyGroup("Response", new String[]{
            TIMEOUT, RCFIELD, RCSUCCESS,
        });
        p = property(TIMEOUT);
        p.setPropertyEditorClass(IntegerPropertyEditor.class);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, 60000); // 1 minute

        p = property(RCFIELD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, String.valueOf(ISO8583TestElement.RESPONSE_CODE_FIELD_NO));

        p = property(RCSUCCESS);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "00");
    }
}
