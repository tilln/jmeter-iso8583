package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.util.*;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.property.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* This optional configuration element provides simple message templating.
 * It can be used to specify common message fields (such as dates, times, STANs etc.) instead of
 * repeating them for every sampler.
 * Only fields that don't exist will be merged into every ISO8585Sampler in scope.
 * Inner fields take precedence over outer ones.
 */
public class ISO8583Template extends ConfigTestElement
        implements ISO8583TestElement, TestBean, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ISO8583Template.class);

    public static final String FIELDS = "fields";

    public ISO8583Template() {
        setProperty(new CollectionProperty(FIELDS, new ArrayList<>()));
    }

    public void merge(ISO8583Template other) {
        if (other != null) {
            log.debug("Merging {}", other.getFieldsAsProperty());
            other.getFields().forEach(f -> {
                if (!hasField(f.getName())) addField(f);
            });
        }
    }

    protected boolean hasField(String id) {
        for (PropertyIterator it = getFieldsAsProperty().iterator(); it.hasNext(); ) {
            MessageField own = (MessageField) it.next().getObjectValue();
            if (own.getName().equals(id)) return true;
        }
        return false;
    }

    protected CollectionProperty getFieldsAsProperty() {
        return (CollectionProperty) getProperty(FIELDS);
    }

    protected void addField(MessageField field) {
        log.debug("Add field {}", field);
        JMeterProperty prop = AbstractProperty.createProperty(field);
        if (isRunningVersion()) {
            this.setTemporary(prop); // so fields added at runtime are removed automatically
        }
        getFieldsAsProperty().addProperty(prop);
    }

    protected void addFields(Collection<MessageField> fields) {
        fields.forEach(f -> addField(f));
    }

    // Need Collection getter/setter for TestBean GUI
    public Collection<MessageField> getFields() {
        Collection<MessageField> fields = new ArrayList<>();
        getFieldsAsProperty().iterator().forEachRemaining(f -> fields.add((MessageField) f.getObjectValue()));
        return fields;
    }

    public void setFields(Collection<MessageField> fields) {
        getFieldsAsProperty().setCollection(fields);
    }
}
