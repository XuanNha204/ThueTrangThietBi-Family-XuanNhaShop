package vn.xuannhashop;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.xuannhashop.business.pricing.plugins.DailyPricingPlugin;
import vn.xuannhashop.business.maintenance.plugins.InspectionPlugin;
import vn.xuannhashop.business.payroll.plugins.PayrollPlugin;
import vn.xuannhashop.business.shared.DomainException;
import static org.assertj.core.api.Assertions.*;
class RulePluginsTest {
    @Test void dailyPolicyRoundsStartedDaysAndKeepsDepositSeparate(){var p=new DailyPricingPlugin();var from=Instant.parse("2026-11-06T00:00:00Z");var q=p.quote(from,from.plusSeconds(86400).plusNanos(1),new BigDecimal("100000.00"),new BigDecimal("500000.00"),2);assertThat(q.days()).isEqualTo(2);assertThat(q.rental()).isEqualByComparingTo("400000");assertThat(q.deposit()).isEqualByComparingTo("1000000");}
    @Test void invalidIntervalsAreRejected(){var p=new DailyPricingPlugin();var at=Instant.now();assertThatThrownBy(()->p.quote(at,at,BigDecimal.TEN,BigDecimal.TEN,1)).isInstanceOf(DomainException.class);}
    @Test void safetyRequiresEveryCriterion(){var p=new InspectionPlugin();assertThat(p.safe(true,true,true)).isTrue();assertThat(p.safe(false,true,true)).isFalse();assertThat(p.safe(true,false,true)).isFalse();assertThat(p.safe(true,true,false)).isFalse();}
    @Test void payrollUsesExactDecimalArithmetic(){assertThat(new PayrollPlugin().calculate(new BigDecimal("7.25"),new BigDecimal("45000"))).isEqualByComparingTo("326250.00");}
}
