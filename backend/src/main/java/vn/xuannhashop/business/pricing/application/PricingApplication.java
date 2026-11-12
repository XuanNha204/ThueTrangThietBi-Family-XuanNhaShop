package vn.xuannhashop.business.pricing.application;
import java.math.BigDecimal;
import java.time.Instant;
import vn.xuannhashop.business.rules.PricingRule;
public interface PricingApplication {
    PricingRule.Quote quote(String version,Instant from,Instant to,BigDecimal daily,BigDecimal deposit,int quantity);
    java.util.Map<String,Object> policy();
    void lockPrices(java.util.List<java.util.UUID> types);
}
