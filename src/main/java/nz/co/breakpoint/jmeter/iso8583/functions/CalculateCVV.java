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
        String cvk = values[0].execute().trim();
        String pan = values[1].execute().trim();
        String exp = values[2].execute().trim();
        String sc = values[3].execute().trim();

        if (cvk.isEmpty())
            throw new InvalidVariableException("CVV key must not be empty");
        if (pan.isEmpty())
            throw new InvalidVariableException("PAN must not be empty");
        if (exp.isEmpty())
            throw new InvalidVariableException("Expiry date must not be empty");
        if (sc.isEmpty())
            throw new InvalidVariableException("Service Code must not be empty");

        String cvv = securityModule.calculateCVV(pan, cvk, exp, sc);
        addVariableValue(cvv, values, 4);
        return cvv;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        checkParameterCount(parameters, 4, 5);
        values = parameters.toArray(new CompoundVariable[0]);
    }
}
