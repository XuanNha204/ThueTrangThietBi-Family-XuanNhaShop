package vn.xuannhashop.business.finance.application;
import java.math.BigDecimal;
import java.util.*;
public interface FinanceRepository {
    FinanceApplication.Balance balances(UUID order);
    Map<String,Object> lockOrder(UUID order); Map<String,Object> lockApproval(UUID id);
    List<Map<String,Object>> ledger(); List<Map<String,Object>> approvals();
    void record(UUID id,UUID order,UUID payroll,String kind,BigDecimal amount,String key,String actor,String note,UUID approval);
    void request(UUID id,UUID order,BigDecimal amount,String actor,String reason);
    BigDecimal pendingRefunds(UUID order); boolean inspected(UUID order);
    void decide(UUID id,boolean approved,String actor);
    void addCharge(UUID order,UUID incident,BigDecimal amount,String reason,String approver);
}
