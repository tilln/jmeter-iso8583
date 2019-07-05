package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testbeans.TestBean;

/* Marker interface for GUI classes plus some common constants
 */
public interface ISO8583TestElement extends TestBean {
    int RESPONSE_CODE_FIELD_NO = 39,
        PIN_FIELD_NO = 52,
        MAC_FIELD_NO = 64;
}
