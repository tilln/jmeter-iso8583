package nz.co.breakpoint.jmeter.iso8583;

import javax.script.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.jmeter.util.JMeterUtils;
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

    protected String scriptFilename;
    protected CompiledScript compiledScript;
    protected Bindings bindings;

    @Override
    public void setConfiguration(Configuration cfg) {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");
        if (engine == null) {
            log.error("Groovy script engine not found! Check JMeter installation.");
            return;
        }
        bindings = engine.createBindings();
        bindings.put("log", log);
        bindings.put("props", JMeterUtils.getJMeterProperties());

        scriptFilename = cfg.get("source");
        log.info("Compiling script {}", scriptFilename);

        try (Reader fileReader = new FileReader(scriptFilename)) {
            compiledScript = ((Compilable) engine).compile(fileReader);
        } catch (FileNotFoundException e) {
            log.error("Script file not found {}", scriptFilename);
        } catch (ScriptException e) {
            log.error("Error compiling script {}, error: {}", scriptFilename, e.getMessage());
        } catch (IOException e) {
            log.error("Error closing file {}", scriptFilename, e);
        }
    }

    @Override
    public boolean process(ISOSource source, ISOMsg message) {
        if (compiledScript != null) {
            bindings.put("message", message);
            bindings.put("source", source);
            try {
                compiledScript.eval(bindings);
                return true;
            } catch (ScriptException e) {
                log.error("Error in script {}", scriptFilename, e.getCause());
            }
        }
        return false;
    }
}
