package vn.xuannhashop.business.finance.application;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public interface AccountingApplication {
    record Expense(UUID supplierId,UUID purchaseReceiptId,UUID equipmentId,String category,LocalDate incurredOn,LocalDate dueOn,BigDecimal amount,String documentRef,String description) {}
    record Reconciliation(Instant startsAt,Instant endsAt,BigDecimal openingBalance,BigDecimal countedBalance,String reason) {}
    Map<String,Object> overview();
    UUID supplier(String name,String contact,String documentRef,String key);
    UUID expense(Expense input,String key);
    void decideExpense(UUID id,boolean accepted,String note,String key);
    UUID pay(UUID expense,BigDecimal amount,String evidence,String key);
    UUID reconcile(Reconciliation input,String key);
    void decideReconciliation(UUID id,boolean accepted,String note,String key);
}
