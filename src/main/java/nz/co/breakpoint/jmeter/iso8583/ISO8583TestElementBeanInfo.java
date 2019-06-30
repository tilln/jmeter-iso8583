package nz.co.breakpoint.jmeter.iso8583;

import java.util.ResourceBundle;
import org.apache.jmeter.testbeans.BeanInfoSupport;

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

}
