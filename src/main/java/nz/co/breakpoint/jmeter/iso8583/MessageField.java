package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testelement.AbstractTestElement;

/* Represents an entry in the ISO8583Sampler's message fields table.
 * The Content string will be interpreted depending on the field packager class,
 * i.e. hex digits for binary fields, chars for ASCII fields etc.
 */
public class MessageField extends AbstractTestElement {

    private static final String CONTENT = "content", TAG = "tag";

    public MessageField() {}

    // package access for unit tests
    MessageField(String name, String content) {
        this(name, content, "");
    }

    MessageField(String name, String content, String tag) {
        setName(name);
        setTag(tag);
        setContent(content);
    }

    public String getContent() { return getPropertyAsString(CONTENT); }
    public void setContent(String content) { setProperty(CONTENT, content); }

    public String getTag() { return getPropertyAsString(TAG); }
    public void setTag(String tag) { setProperty(TAG, tag); }

    // Mainly for debug output
    @Override
    public String toString() {
        return getTag().isEmpty()? "DE"+getName()+"="+getContent()
            : "DE"+getName()+"="+getTag()+":"+getContent();
    }
}
