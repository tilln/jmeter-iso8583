package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.q2.Q2;
import org.junit.rules.ExternalResource;

public class Q2ServerResource extends ExternalResource {
    Q2 q2;

    public Q2ServerResource() {
        q2 = new Q2("src/test/resources/q2/");
    }

    protected void before() {
        q2.start();
        assert q2.ready(5000); // give it 5 seconds to start up
    }

    protected void after() {
        q2.shutdown(true);
    }
}
