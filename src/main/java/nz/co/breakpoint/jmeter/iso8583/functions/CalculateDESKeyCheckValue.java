package nz.co.breakpoint.jmeter.iso8583.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import java.util.Collection;

public class CalculateDESKeyCheckValue extends AbstractCryptoFunction {

    @Override
    public String getReferenceKey() { return "__calculateDESKeyCheckValue"; }

    @Override
    public String execute(SampleResult prev, Sampler sampler) throws InvalidVariableException {
        String clearKey = values[0].execute().trim();

        if (clearKey.isEmpty())
            throw new InvalidVariableException("Clear key must not be empty");

        String kcv = securityModule.calculateKeyCheckValue(clearKey);

        addVariableValue(kcv, values, 1);

        return kcv;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        checkParameterCount(parameters, 1, 2);
        values = parameters.toArray(new CompoundVariable[0]);
    }
}
