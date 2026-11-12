package vn.xuannhashop.business.rules;
import java.math.BigDecimal;
import java.time.Instant;
public interface PricingRule {
    record Quote(long days,BigDecimal rental,BigDecimal deposit,String version) {}
    Quote quote(Instant from,Instant to,BigDecimal dailyRate,BigDecimal deposit,int quantity);
}
