package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Config.*;
import org.apache.jmeter.testbeans.gui.FileEditor;
import org.apache.jmeter.testbeans.gui.TableEditor;

/* Describes the ISO8583Config GUI.
 * Most values may be left undefined so they can be merged with other config elements.
 */
public class ISO8583ConfigBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583ConfigBeanInfo() {
        super(ISO8583Config.class);

        PropertyDescriptor p;

        createPropertyGroup("Channel", new String[]{
            CLASSNAME, PACKAGER, HEADER, HOST, PORT, CONFIG,
        });

        p = property(CLASSNAME);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, getDefaultChannelClass());
        p.setValue(TAGS, getChannelClasses());

        p = property(PACKAGER);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setPropertyEditorClass(FileEditor.class);
        p.setValue(DEFAULT, "");

        p = property(HEADER);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(HOST);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(PORT);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(CONFIG);
        p.setPropertyEditorClass(TableEditor.class);
        p.setValue(TableEditor.CLASSNAME, ChannelConfigItem.class.getName());
        p.setValue(TableEditor.HEADERS, getTableHeadersWithDefaults(CONFIG+".tableHeaders",
            new String[]{"Name", "Value"}));
        p.setValue(TableEditor.OBJECT_PROPERTIES,
            new String[]{"name", ChannelConfigItem.VALUE}); // name is a standard TestElement member
        p.setValue(TableEditor.OBJECT_PROPERTIES, new String[]{"name", "value"});

        createPropertyGroup("SSL", new String[]{
             KEYSTORE, STOREPASSWORD, KEYPASSWORD,
        });

        p = property(KEYSTORE);
        p.setPropertyEditorClass(FileEditor.class);
        p.setValue(DEFAULT, "");

        p = property(STOREPASSWORD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(KEYPASSWORD);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
    }
}
