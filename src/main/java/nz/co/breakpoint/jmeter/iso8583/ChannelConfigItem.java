package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testelement.AbstractTestElement;

/** Represents an entry in the ISO8583Config's Channel configuration table.
 * Only needed to copy JMeter Properties across.
 */
public class ChannelConfigItem extends AbstractTestElement {

    static final String VALUE = "value";

    public ChannelConfigItem() {}

    public String getValue() { return getPropertyAsString(VALUE); }
    public void setValue(String value) { setProperty(VALUE, value); }

    // Mainly for debug output
    @Override
    public String toString() {
        return getName()+":"+getValue();
    }
}
