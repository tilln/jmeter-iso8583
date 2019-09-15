package nz.co.breakpoint.jmeter.iso8583;

import nz.co.breakpoint.jmeter.iso8583.functions.CalculatePINBlock;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CalculatePINBlockTest extends ISO8583TestBase {
    CalculatePINBlock instance = new CalculatePINBlock();

    @Test
    public void shouldCalculatePINBlock() throws InvalidVariableException {
        CompoundVariable pin = new CompoundVariable("0000"),
            format = new CompoundVariable("1"),
            pan = new CompoundVariable("4444333322221111");
        String pinBlock = "040043cccddddeee";

        instance.setParameters(Arrays.asList(pin, format, pan, new CompoundVariable("pinblock")));
        assertEquals(pinBlock, instance.execute(null, null));
        assertEquals(pinBlock, ctx.context.getVariables().get("pinblock"));
    }
}
