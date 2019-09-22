package nz.co.breakpoint.jmeter.iso8583.functions;

import nz.co.breakpoint.jmeter.iso8583.SecurityModule;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.util.JMeterUtils;
import org.jpos.iso.ISOUtil;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public abstract class AbstractCryptoFunction extends AbstractFunction {
    protected CompoundVariable[] values;
    protected SecurityModule securityModule = new SecurityModule();
    protected List<String> argumentDesc;

    public AbstractCryptoFunction() {
        ResourceBundle res = ResourceBundle.getBundle(getClass().getName() + "Resources",
                JMeterUtils.getLocale(), getClass().getClassLoader());
        argumentDesc = res.keySet().stream().sorted().map(res::getString).collect(Collectors.toList());
    }

    @Override
    public List<String> getArgumentDesc() { return argumentDesc; }
}
