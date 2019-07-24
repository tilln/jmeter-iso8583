package nz.co.breakpoint.jmeter.iso8583;

import static nz.co.breakpoint.jmeter.iso8583.ISO8583Component.FIELDS;

/** Describes the ISO8583Component GUI.
 * Uses the same message fields as the ISO8583Sampler GUI.
 */
public class ISO8583ComponentBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583ComponentBeanInfo() {
        super(ISO8583Component.class);

        createMessageFieldsTableProperty(FIELDS);
    }
}