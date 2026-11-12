package vn.xuannhashop.presentation.api;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.finance.application.SePayPaymentApplication;

@RestController
@RequestMapping("/api")
public class SePayPaymentController {
    private final SePayPaymentApplication payments;
    public SePayPaymentController(SePayPaymentApplication payments){this.payments=payments;}
    record RequestInput(String kind) {}
    record MatchInput(UUID requestId,String reason) {}
    record Reason(String reason) {}
    @GetMapping("/finance/sepay/configuration") Object configuration(){return payments.configuration();}
    @PostMapping("/finance/sepay/configuration") void configure(@RequestBody SePayPaymentApplication.BankConfiguration c,@RequestHeader("Idempotency-Key")String key){payments.configure(c,key);}
    @GetMapping("/rentals/{order}/payments") Object requests(@PathVariable UUID order){return payments.requests(order);}
    @PostMapping("/rentals/{order}/payments") Object create(@PathVariable UUID order,@RequestBody RequestInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",payments.create(order,c.kind(),key));}
    @PostMapping("/payments/{id}/cancel") void cancel(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){payments.cancel(id,key);}
    @PostMapping("/finance/sepay/events/{id}/match") Object match(@PathVariable UUID id,@RequestBody MatchInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",payments.reconcile(id,c.requestId(),c.reason(),key));}
    @PostMapping("/finance/sepay/events/{id}/ignore") void ignore(@PathVariable UUID id,@RequestBody Reason c,@RequestHeader("Idempotency-Key")String key){payments.ignore(id,c.reason(),key);}
    @PostMapping("/finance/sepay/events/{id}/retry") void retry(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){payments.retry(id,key);}
}
