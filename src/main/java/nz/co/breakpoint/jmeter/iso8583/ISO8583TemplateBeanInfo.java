package nz.co.breakpoint.jmeter.iso8583;

import static nz.co.breakpoint.jmeter.iso8583.ISO8583Template.FIELDS;

public class ISO8583TemplateBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583TemplateBeanInfo() {
        super(ISO8583Template.class);

        createMessageFieldsTableProperty(FIELDS);
    }
}