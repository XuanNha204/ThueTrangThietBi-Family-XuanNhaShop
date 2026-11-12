package vn.xuannhashop.business.finance.application;
import java.math.BigDecimal;
import java.util.*;
public interface FinanceApplication {
    record Balance(BigDecimal depositHeld,BigDecimal rentalPaid,BigDecimal chargesDue,BigDecimal chargesPaid) {}
    Balance balances(UUID order);
    List<Map<String,Object>> ledger(); List<Map<String,Object>> approvals();
    UUID receive(UUID order,String kind,BigDecimal amount,String note,String key);
    UUID requestRefund(UUID order,BigDecimal amount,String reason,String key);
    void decide(UUID approval,boolean approved,String key);
    void payPayroll(UUID payroll,BigDecimal amount,String key);
    void payAdvance(UUID adjustment,BigDecimal amount,String note,String key);
    void addCharge(UUID order,UUID incident,BigDecimal amount,String reason);
}
