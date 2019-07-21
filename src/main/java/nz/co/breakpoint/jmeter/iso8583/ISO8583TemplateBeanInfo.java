package nz.co.breakpoint.jmeter.iso8583;

import static nz.co.breakpoint.jmeter.iso8583.ISO8583Template.FIELDS;

/** Describes the ISO8583Template GUI.
 * Uses the same message fields as the ISO8583Sampler GUI.
 */
public class ISO8583TemplateBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583TemplateBeanInfo() {
        super(ISO8583Template.class);

        createMessageFieldsTableProperty(FIELDS);
    }
}