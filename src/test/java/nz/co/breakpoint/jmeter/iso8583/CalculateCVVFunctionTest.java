package nz.co.breakpoint.jmeter.iso8583;

import nz.co.breakpoint.jmeter.iso8583.functions.CalculateCVV;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;

public class CalculateCVVFunctionTest extends ISO8583TestBase {
    CalculateCVV instance = new CalculateCVV();

    @Test
    public void shouldCalculateCVV() throws InvalidVariableException {
        CompoundVariable pan = new CompoundVariable("4444333322221111"),
            exp = new CompoundVariable("9911"),
            cvk = new CompoundVariable(DEFAULT_3DES_KEY);

        instance.setParameters(Arrays.asList(cvk, pan, exp, new CompoundVariable("101"), new CompoundVariable("CVV")));
        assertEquals("662", instance.execute(null, null));
        assertEquals("662", ctx.context.getVariables().get("CVV"));

        instance.setParameters(Arrays.asList(cvk, pan, exp, new CompoundVariable("000")));  // CVV2
        assertEquals("114", instance.execute(null, null));

        instance.setParameters(Arrays.asList(cvk, pan, exp, new CompoundVariable("999"))); // iCVV
        assertEquals("163", instance.execute(null, null));
    }
}
