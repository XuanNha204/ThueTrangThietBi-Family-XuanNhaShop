package vn.xuannhashop.business.finance.application;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import vn.xuannhashop.business.finance.application.AccountingApplication.*;
public interface AccountingRepository {
    Map<String,Object> overview();
    void supplier(UUID id,String name,String contact,String document,String actor);
    boolean supplierExists(UUID id);boolean equipmentExists(UUID id);
    Map<String,Object> purchase(UUID id);
    void expense(UUID id,Expense c,String actor);
    Map<String,Object> expense(UUID id);
    void decideExpense(UUID id,boolean accepted,String actor,String note);
    BigDecimal paid(UUID id);
    void pay(UUID id,UUID expense,BigDecimal amount,String evidence,String actor,String key);
    void ledgerLock();
    Map<String,Object> ledgerSnapshot(Instant from,Instant to);
    boolean overlaps(Instant from,Instant to);
    void reconcile(UUID id,Reconciliation c,Map<String,Object> ledger,String actor);
    Map<String,Object> reconciliation(UUID id);
    void decideReconciliation(UUID id,boolean accepted,String actor,String note);
}
