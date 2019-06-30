package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testelement.AbstractTestElement;

/* Represents an entry in the ISO8583Sampler's message fields table.
 * Binary field content can be created via JMeter's __char() function.
 * TODO test binaries
 */
public class MessageField extends AbstractTestElement {

    private static final String TAG = "tag", CONTENT = "content";

    public MessageField() {}

    // package access for unit tests
    MessageField(String name, String content) {
        this(name, "", content);
    }

    MessageField(String name, String tag, String content) {
        setName(name);
        setTag(tag);
        setContent(content);
    }

    public String getTag() { return getPropertyAsString(TAG); }
    public void setTag(String tag) { setProperty(TAG, tag); }

    public String getContent() { return getPropertyAsString(CONTENT); }
    public void setContent(String content) { setProperty(CONTENT, content); }

    // Mainly for debug output
    @Override
    public String toString() {
        return getTag().isEmpty()? "DE"+getName()+"="+getContent()
            : "DE"+getName()+"="+getTag()+":"+getContent();
    }
}
