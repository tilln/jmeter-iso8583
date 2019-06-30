package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Config.*;
import org.apache.jmeter.testbeans.gui.FileEditor;

/* Describes the ISO8583Config GUI.
 * Most values may be left undefined so they can be merged with other config elements.
 */
public class ISO8583ConfigBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583ConfigBeanInfo() {
        super(ISO8583Config.class);

        createPropertyGroup("Channel", new String[]{
            CLASSNAME, PACKAGER, HEADER, HOST, PORT,
        });
        PropertyDescriptor p;

        p = property(CLASSNAME);
        p.setValue(DEFAULT, getDefaultChannelClass());
        p.setValue(TAGS, getChannelClasses());

        p = property(PACKAGER);
        p.setPropertyEditorClass(FileEditor.class);
        p.setValue(DEFAULT, "");

        p = property(HEADER);
        p.setValue(DEFAULT, "");

        p = property(HOST);
        p.setValue(DEFAULT, "");

        p = property(PORT);
        p.setValue(DEFAULT, "");
    }
}
