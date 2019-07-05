package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testbeans.gui.IntegerPropertyEditor;
import org.apache.jmeter.testbeans.gui.TableEditor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Sampler.*;
import java.beans.PropertyDescriptor;

/* Describes the sampler GUI.
 */
public class ISO8583SamplerBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583SamplerBeanInfo() {
        super(ISO8583Sampler.class);

        PropertyDescriptor p;

        createPropertyGroup("Request", new String[]{
            FIELDS,
        });
        p = property(FIELDS);
        p.setPropertyEditorClass(TableEditor.class);
        p.setValue(TableEditor.CLASSNAME, MessageField.class.getName());
        p.setValue(TableEditor.HEADERS, getTableHeadersWithDefaults("fields.tableHeaders",
            new String[]{"Field", "Content", "Tag", "Comment"}));
        p.setValue(TableEditor.OBJECT_PROPERTIES, new String[]{"name", "content", "tag", "comment"});

        createPropertyGroup("Response", new String[]{
            TIMEOUT,
        });
        p = property(TIMEOUT);
        p.setPropertyEditorClass(IntegerPropertyEditor.class);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, 60000); // 1 minute

    }
}
