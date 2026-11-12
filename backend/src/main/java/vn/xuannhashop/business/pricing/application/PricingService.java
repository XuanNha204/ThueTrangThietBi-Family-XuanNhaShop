package vn.xuannhashop.business.pricing.application;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import vn.xuannhashop.business.rules.*;
@Service
public class PricingService implements PricingApplication {
    private final RuleEngine engine;private final Map<String,PricingRule> rules;
    @Value("${app.hold-minutes}") private int holdMinutes;
    @Value("${app.buffer-minutes}") private int bufferMinutes;
    private final ProductPriceRepository prices;
    public PricingService(RuleEngine e,Map<String,PricingRule> r,ProductPriceRepository prices){engine=e;rules=r;this.prices=prices;}
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockPrices(java.util.List<java.util.UUID> types){prices.lockForRental(types);}
    public PricingRule.Quote quote(String version,Instant from,Instant to,BigDecimal daily,BigDecimal deposit,int quantity){return engine.select(rules,version).quote(from,to,daily,deposit,quantity);}
    public Map<String,Object> policy(){return Map.of("application","XuanNhaShop","version","0.2.0","pricingVersion","DEV-1","holdMinutes",holdMinutes,"bufferMinutes",bufferMinutes,"timezone","Asia/Ho_Chi_Minh","environment","dev");}
}
