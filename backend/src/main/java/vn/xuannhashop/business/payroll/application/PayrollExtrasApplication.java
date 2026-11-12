package vn.xuannhashop.business.payroll.application;
import java.util.*;import java.math.BigDecimal;
public interface PayrollExtrasApplication {
    record Adjustment(UUID staffId,String period,String kind,BigDecimal amount,String basis){}
    Map<String,Object> overview();Map<String,Object> mine();
    UUID link(UUID staff,UUID account,String key);UUID adjustment(Adjustment c,String key);
    void decide(UUID id,boolean accepted,String note,String key);
    UUID question(UUID payroll,String question,String key);void answer(UUID id,String answer,String key);
}
