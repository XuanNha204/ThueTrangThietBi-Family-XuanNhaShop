package vn.xuannhashop.business.rules;
import java.math.BigDecimal;
public interface PayrollRule { BigDecimal calculate(BigDecimal approvedHours,BigDecimal hourlyRate); }
