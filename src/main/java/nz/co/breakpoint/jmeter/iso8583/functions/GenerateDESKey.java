package nz.co.breakpoint.jmeter.iso8583.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.util.Collection;

public class GenerateDESKey extends AbstractCryptoFunction {

        @Override
        public String getReferenceKey() { return "__generateDESKey"; }

        @Override
        public String execute(SampleResult prev, Sampler sampler) throws InvalidVariableException {
            String length = values[0].execute().trim();

            if (length.isEmpty())
                throw new InvalidVariableException("Key length must not be empty");

            String key = securityModule.generateDESKey(length);

            addVariableValue(key, values, 1);

            return key;
        }

        @Override
        public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
            checkParameterCount(parameters, 1, 2);
            values = parameters.toArray(new CompoundVariable[0]);
        }
    }
