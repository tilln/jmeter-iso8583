package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testbeans.TestBean;

/* Marker interface for GUI classes plus some common constants
 */
public interface ISO8583TestElement extends TestBean {
    int RESPONSE_CODE_FIELD_NO = 39,
        PIN_FIELD_NO = 52,
        KSN_FIELD_NO = 53,
        ICC_FIELD_NO = 55,
        MAC_FIELD_NO = 64;

    // JMeter config properties names:
    String CHANNEL_RECONNECT_DELAY = "jmeter.iso8583.channelReconnectDelay",
        Q2_DEPLOY_DIR = "jmeter.iso8583.q2DeployDir",
        Q2_STARTUP_TIMEOUT = "jmeter.iso8583.q2StartupTimeout",
        INCOMING_CONNECTION_TIMEOUT = "jmeter.iso8583.incomingConnectionTimeout",
        ARQC_INPUT_TAGS = "jmeter.iso8583.arqcInputTags",
        BINARY_FIELD_TAGS = "jmeter.iso8583.binaryFieldTags",
        KSN_DESCRIPTOR = "jmeter.iso8583.ksnDescriptor";

    String TAG_SEPARATOR_REGEX = "[,;:. ]";
}
