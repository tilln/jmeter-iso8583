package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.util.LogEvent;
import org.jpos.util.LogListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import static org.slf4j.event.Level.*;

/** Adapter class between JMeter (slf4j) and jPOS logging subsystems.
 * jPOS log events are not printed as XML but the details are unwrapped and printed as individual lines.
 * They are logged as a pseudo class n.c.b.j.i.Q2 so can be filtered for.
 * A log event's Tag is used to map to an slf4j log level.
 */
public class Slf4jLogListener implements LogListener {

    @Override
    public LogEvent log(LogEvent evt) {
        // Pseudo class for Q2 log output:
        final Logger log = LoggerFactory.getLogger(Slf4jLogListener.class.getPackage().getName()+".Q2");

        // Try to figure out appropriate log level:
        Level level = null;
        final String tag = evt.getTag();
        try {
            level = Level.valueOf(tag.toUpperCase()); // only some tags can be mapped directly to a level
        } catch (IllegalArgumentException ignore) {}

        StringBuilder format = new StringBuilder();
        format.append("(").append(evt.getRealm()).append(") ");
        if (level == null) {
            level = DEBUG; // fall back to debug for all other tags
            format.append("[").append(tag).append("] ");
        }
        format.append("{}");

        // Work-around for some Exceptions being logged only as strings (e.g. SSL socket related IOException):
        if (evt.toString().contains("Exception"))
            level = ERROR;

        for (Object o : evt.getPayLoad()) {
            final String line = o.toString();
            if (o instanceof Throwable)
                log.error(format.toString(), ((Throwable) o).getMessage(), (Throwable) o);
            else
                log(log, level, format.toString(), line);
        }
        return evt;
    }

    // The slf4j API has no higher-level log method
    protected void log(Logger log, Level level, String format, Object... args) {
        switch (level) {
            case ERROR:
                log.error(format, args);
                return;
            case WARN:
                log.warn(format, args);
                return;
            case INFO:
                log.info(format, args);
                return;
            case DEBUG:
                log.debug(format, args);
                return;
            case TRACE:
                log.trace(format, args);
                return;
            default:
        }
    }
}
