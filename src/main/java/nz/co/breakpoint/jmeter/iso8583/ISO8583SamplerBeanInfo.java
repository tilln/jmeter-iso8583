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

        createPropertyGroup("Message", new String[]{
            TIMEOUT, FIELDS,
        });
        PropertyDescriptor p;

        p = property(TIMEOUT);
        p.setPropertyEditorClass(IntegerPropertyEditor.class);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, 60000); // 1 minute

        p = property(FIELDS);
        p.setPropertyEditorClass(TableEditor.class);
        p.setValue(TableEditor.CLASSNAME, MessageField.class.getName());
        p.setValue(TableEditor.HEADERS, getTableHeadersWithDefaults("fields.tableHeaders",
            new String[]{"Field", "Content", "Tag"}));
        p.setValue(TableEditor.OBJECT_PROPERTIES, new String[]{"name", "content", "tag"});

        createPropertyGroup("Cryptograms", new String[]{
            PINBLOCK, PINKEY, MACALGORITHM, MACKEY,
        });

        p = property(PINBLOCK);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(PINKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

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
