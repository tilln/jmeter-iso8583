package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testelement.AbstractTestElement;

/** Base class for Key/Value configuration items.
 */
public class KeyValueConfigItem extends AbstractTestElement {

    static final String VALUE = "value";

    public String getValue() { return getPropertyAsString(VALUE); }
    public void setValue(String value) { setProperty(VALUE, value); }

    // Mainly for debug output
    @Override
    public String toString() {
        return getName()+":"+getValue();
    }
}
