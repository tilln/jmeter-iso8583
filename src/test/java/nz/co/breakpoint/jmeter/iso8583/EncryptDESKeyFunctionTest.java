package nz.co.breakpoint.jmeter.iso8583;

import nz.co.breakpoint.jmeter.iso8583.functions.EncryptDESKey;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.junit.Test;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EncryptDESKeyFunctionTest extends ISO8583TestBase {
    EncryptDESKey instance = new EncryptDESKey();

    final String expected = "2911cf5e94d33fe1";

    @Test
    public void shouldEncryptWithDESKey() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(
            new CompoundVariable(DEFAULT_DES_KEY),
            new CompoundVariable(DEFAULT_DES_KEY)
        ));
        assertEquals(expected, instance.execute(null, null));

        instance.setParameters(Arrays.asList(
            new CompoundVariable(DEFAULT_3DES_KEY),
            new CompoundVariable(DEFAULT_DES_KEY)
        ));
        assertEquals(expected+expected, instance.execute(null, null));
    }

    @Test
    public void shouldEncryptWithTripleDESKey() throws InvalidVariableException {
        instance.setParameters(Arrays.asList(
            new CompoundVariable(DEFAULT_DES_KEY),
            new CompoundVariable(DEFAULT_3DES_KEY)
        ));
        assertEquals(expected, instance.execute(null, null));

        instance.setParameters(Arrays.asList(
            new CompoundVariable(DEFAULT_3DES_KEY),
            new CompoundVariable(DEFAULT_3DES_KEY)
        ));
        assertEquals(expected+expected, instance.execute(null, null));
    }
}
