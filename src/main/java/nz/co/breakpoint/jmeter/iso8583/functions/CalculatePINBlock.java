package nz.co.breakpoint.jmeter.iso8583.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.util.Collection;

public class CalculatePINBlock extends AbstractCryptoFunction {

    @Override
    public String getReferenceKey() { return "__calculatePINBlock"; }

    @Override
    public String execute(SampleResult sampleResult, Sampler sampler) throws InvalidVariableException {
        String pin = values[0].execute();
        String format = values[1].execute();
        String pan = values[2].execute();

        if (pan == null || pan.isEmpty())
            throw new InvalidVariableException("Account number must not be empty");
        if (pin == null || pin.isEmpty())
            throw new InvalidVariableException("PIN must not be empty");

        String pinBlock = securityModule.calculatePINBlock(pin, format, pan);

        addVariableValue(pinBlock, values, 3);
        return pinBlock;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        checkParameterCount(parameters, 3, 4);
        values = parameters.toArray(new CompoundVariable[parameters.size()]);
    }
}
