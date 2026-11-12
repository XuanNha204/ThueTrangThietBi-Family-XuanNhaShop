package vn.xuannhashop.presentation.api;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.finance.application.AccountingApplication;
import vn.xuannhashop.business.finance.application.AccountingApplication.*;
@RestController @RequestMapping("/api/accounting")
public class AccountingController {
    private final AccountingApplication app;public AccountingController(AccountingApplication app){this.app=app;}
    record Supplier(String name,String contact,String documentRef){}record Decision(boolean accepted,String note){}record Payment(BigDecimal amount,String evidence){}
    @GetMapping Object overview(){return app.overview();}
    @PostMapping("/suppliers") Object supplier(@RequestBody Supplier c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.supplier(c.name(),c.contact(),c.documentRef(),key));}
    @PostMapping("/expenses") Object expense(@RequestBody Expense c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.expense(c,key));}
    @PostMapping("/expenses/{id}/decide") void decision(@PathVariable UUID id,@RequestBody Decision c,@RequestHeader("Idempotency-Key")String key){app.decideExpense(id,c.accepted(),c.note(),key);}
    @PostMapping("/expenses/{id}/pay") Object pay(@PathVariable UUID id,@RequestBody Payment c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.pay(id,c.amount(),c.evidence(),key));}
    @PostMapping("/reconciliations") Object reconciliation(@RequestBody Reconciliation c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.reconcile(c,key));}
    @PostMapping("/reconciliations/{id}/decide") void reconcile(@PathVariable UUID id,@RequestBody Decision c,@RequestHeader("Idempotency-Key")String key){app.decideReconciliation(id,c.accepted(),c.note(),key);}
}
