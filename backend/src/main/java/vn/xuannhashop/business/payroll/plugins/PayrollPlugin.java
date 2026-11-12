package vn.xuannhashop.business.payroll.plugins;
import java.math.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.rules.PayrollRule;
@Component("payrollDEV1")
public class PayrollPlugin implements PayrollRule {
    public BigDecimal calculate(BigDecimal hours,BigDecimal rate){return hours.multiply(rate).setScale(2,RoundingMode.HALF_UP);}
}
