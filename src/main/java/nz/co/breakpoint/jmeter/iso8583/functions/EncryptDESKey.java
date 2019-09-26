package nz.co.breakpoint.jmeter.iso8583.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import java.util.Collection;

public class EncryptDESKey extends AbstractCryptoFunction {

    @Override
    public String getReferenceKey() { return "__encryptDESKey"; }

    @Override
    public String execute(SampleResult prev, Sampler sampler) throws InvalidVariableException {
        String clearKey = values[0].execute().trim();
        String encryptingKey = values[1].execute().trim();

        if (clearKey.isEmpty())
            throw new InvalidVariableException("Clear key must not be empty");
        if (encryptingKey.isEmpty())
            throw new InvalidVariableException("Encrypting key must not be empty");

        String encryptedKey = securityModule.encryptDESKey(clearKey, encryptingKey);

        addVariableValue(encryptedKey, values, 2);

        return encryptedKey;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        checkParameterCount(parameters, 2, 3);
        values = parameters.toArray(new CompoundVariable[0]);
    }
}
