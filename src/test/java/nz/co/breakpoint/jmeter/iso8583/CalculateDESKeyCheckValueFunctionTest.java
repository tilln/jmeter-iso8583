package nz.co.breakpoint.jmeter.iso8583;

import nz.co.breakpoint.jmeter.iso8583.functions.CalculateDESKeyCheckValue;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;


public class CalculateDESKeyCheckValueFunctionTest extends ISO8583TestBase {
    CalculateDESKeyCheckValue instance = new CalculateDESKeyCheckValue();

    @Test
    public void shouldCalculateKeyCheckValue() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(
            new CompoundVariable(DEFAULT_DES_KEY)
        ));
        assertEquals("a8b7b5", instance.execute(null, null));

        instance.setParameters(Arrays.asList(
            new CompoundVariable(DEFAULT_3DES_KEY)
        ));
        assertEquals("a8b7b5", instance.execute(null, null));
    }
}
