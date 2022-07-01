package nz.co.breakpoint.jmeter.iso8583;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.File;
import java.io.IOException;
import org.apache.jmeter.util.JMeterUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class GroovyRequestListener implements ISORequestListener, Configurable {

    private static final Logger log = LoggerFactory.getLogger(GroovyRequestListener.class);

    protected Script script;

    @Override
    public void setConfiguration(Configuration cfg) {
        GroovyShell shell = new GroovyShell();

        String filename = cfg.get("source");
        log.info("Compiling script {}", filename);

        try {
            script = shell.parse(new File(filename));
        } catch (CompilationFailedException e) {
            log.error("Script compilation failure: {}", e.getMessage());
        } catch (IOException e) {
            log.error("Error accessing file {}", filename, e);
        }
    }

    @Override
    public boolean process(ISOSource source, ISOMsg message) {
        if (script == null) {
            return false;
        }
        Binding bindings = script.getBinding();
        bindings.setVariable("message", message);
        bindings.setVariable("source", source);
        bindings.setVariable("log", log);
        bindings.setVariable("props", JMeterUtils.getJMeterProperties());
        try {
            script.run();
        } catch (Exception e) {
            log.error("Error in script", e);
        }
        return true;
    }
}
