package vn.xuannhashop.business.pricing.plugins;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.rules.PricingRule;
import vn.xuannhashop.business.shared.DomainException;
@Component("DEV-1")
public class DailyPricingPlugin implements PricingRule {
    public Quote quote(Instant from,Instant to,BigDecimal dailyRate,BigDecimal deposit,int quantity){
        if(from==null||to==null||!to.isAfter(from)||quantity<1||quantity>20)throw DomainException.bad("Khoảng thời gian hoặc số lượng không hợp lệ.");
        var duration=Duration.between(from,to);var seconds=duration.getSeconds()+(duration.getNano()>0?1:0);long days=Math.max(1,(seconds+86399)/86400);
        if(days>366)throw DomainException.bad("Bản dev hỗ trợ đơn tối đa 366 ngày.");
        return new Quote(days,dailyRate.multiply(BigDecimal.valueOf(days*quantity)),deposit.multiply(BigDecimal.valueOf(quantity)),"DEV-1");
    }
}
