package nz.co.breakpoint.jmeter.iso8583.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.jpos.iso.ISOUtil;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Collection;

public class CalculateCVV extends AbstractCryptoFunction {

    @Override
    public String getReferenceKey() { return "__calculateCVV"; }

    @Override
    public String execute(SampleResult prev, Sampler sampler) throws InvalidVariableException {
        try {
            Key cvk = new SecretKeySpec(ISOUtil.hex2byte(values[0].execute()), "DESede");
            String cvv = securityModule.calculateCVV(values[1].execute(), cvk, values[2].execute(), values[3].execute());
            addVariableValue(cvv, values, 4);
            return cvv;
        } catch (IllegalArgumentException e) {
            throw new InvalidVariableException(e);
        }
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        checkParameterCount(parameters, 4, 5);
        values = parameters.toArray(new CompoundVariable[parameters.size()]);
    }
}
