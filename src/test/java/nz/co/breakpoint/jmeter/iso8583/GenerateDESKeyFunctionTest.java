package nz.co.breakpoint.jmeter.iso8583;

import java.util.Arrays;
import nz.co.breakpoint.jmeter.iso8583.functions.GenerateDESKey;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerateDESKeyFunctionTest  extends ISO8583TestBase {
    GenerateDESKey instance = new GenerateDESKey();

    @Test
    public void shouldGenerate3DESKey() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(new CompoundVariable("192"), new CompoundVariable("KEY")));
        String key = instance.execute(null, null);
        assertTrue(key.matches("[0-9a-f]{48}"));
        assertEquals(key, ctx.context.getVariables().get("KEY"));
    }

    @Test
    public void shouldGenerateSingleLengthDESKey() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(new CompoundVariable("64")));
        assertTrue(instance.execute(null, null).matches("[0-9a-f]{16}"));
    }

    @Test
    public void shouldGenerateDoubleLengthDESKey() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(new CompoundVariable("128")));
        assertTrue(instance.execute(null, null).matches("[0-9a-f]{32}"));
    }

    @Test
    public void shouldCheckKeyLength() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(new CompoundVariable("123")));
        assertEquals("", instance.execute(null, null));
    }

    @Test(expected = InvalidVariableException.class)
    public void shouldValidateParameters() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(new CompoundVariable("")));
        assertEquals("", instance.execute(null, null));
    }
}
