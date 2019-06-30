package nz.co.breakpoint.jmeter.iso8583;

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
        p.setValue(DEFAULT, "");

        p = property(FIELDS);
        p.setPropertyEditorClass(TableEditor.class);
        p.setValue(TableEditor.CLASSNAME, MessageField.class.getName());
        p.setValue(TableEditor.HEADERS, getTableHeadersWithDefaults("fields.tableHeaders",
            new String[]{"Field", "Tag", "Content"}));
        p.setValue(TableEditor.OBJECT_PROPERTIES, new String[]{"name", "tag", "content"});

        createPropertyGroup("Cryptograms", new String[]{
            MACALGORITHM, MACKEY,
        });

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
