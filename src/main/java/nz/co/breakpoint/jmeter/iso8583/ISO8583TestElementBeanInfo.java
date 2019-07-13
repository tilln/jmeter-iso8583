package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;
import java.util.ResourceBundle;
import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TableEditor;

public class ISO8583TestElementBeanInfo extends BeanInfoSupport {

    public ISO8583TestElementBeanInfo(Class<? extends ISO8583TestElement> clazz) {
        super(clazz);
    }

    // Convenience method for localized headers of TableEditor columns
    protected String[] getTableHeadersWithDefaults(String resourceName, String[] defaults) {
        ResourceBundle rb = (ResourceBundle)getBeanDescriptor().getValue(RESOURCE_BUNDLE);
        return rb != null && rb.containsKey(resourceName) ? 
            rb.getString(resourceName).split("\\|") : 
            defaults;
    }

    protected PropertyDescriptor createMessageFieldsTableProperty(String propertyName) {
        PropertyDescriptor p = property(propertyName);
        p.setPropertyEditorClass(TableEditor.class);
        p.setValue(TableEditor.CLASSNAME, MessageField.class.getName());
        p.setValue(TableEditor.HEADERS, getTableHeadersWithDefaults(propertyName+".tableHeaders",
                new String[]{"Field", "Content", "Tag", "Comment"}));
        p.setValue(TableEditor.OBJECT_PROPERTIES,
                // name and comment are standard TestElement members:
                new String[]{"name", MessageField.CONTENT, MessageField.TAG, "comment"});
        return p;
    }
}
