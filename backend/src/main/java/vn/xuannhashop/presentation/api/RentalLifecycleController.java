package vn.xuannhashop.presentation.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.rental.application.*;

@RestController
@RequestMapping("/api/rentals")
public class RentalLifecycleController {
    private final RentalChangeApplication changes;private final RentalMonitoringApplication monitoring;private final RentalIncidentApplication incidents;
    public RentalLifecycleController(RentalChangeApplication changes,RentalMonitoringApplication monitoring,RentalIncidentApplication incidents){this.changes=changes;this.monitoring=monitoring;this.incidents=incidents;}
    record Consent(String customerName,String basis) {}
    record Decision(boolean approved,String note) {}
    @GetMapping("/attention") Object attention(){return monitoring.attention();}
    @GetMapping("/{order}/notices") Object notices(@PathVariable UUID order){return monitoring.notices(order);}
    @GetMapping("/{order}/changes") Object history(@PathVariable UUID order){return changes.history(order);}
    @PostMapping("/{order}/changes/quote") Object quote(@PathVariable UUID order,@RequestBody RentalChangeApplication.Request request){return changes.quote(order,request);}
    @PostMapping("/{order}/changes") Object propose(@PathVariable UUID order,@RequestBody RentalChangeApplication.Request request,@RequestHeader("Idempotency-Key")String key){return Map.of("id",changes.propose(order,request,key));}
    @PostMapping("/{order}/changes/{id}/accept") void accept(@PathVariable UUID order,@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){changes.accept(order,id,key);}
    @PostMapping("/{order}/changes/{id}/consent") void consent(@PathVariable UUID order,@PathVariable UUID id,@RequestBody Consent c,@RequestHeader("Idempotency-Key")String key){changes.recordConsent(order,id,c.customerName(),c.basis(),key);}
    @PostMapping("/{order}/changes/{id}/decide") void decide(@PathVariable UUID order,@PathVariable UUID id,@RequestBody Decision d,@RequestHeader("Idempotency-Key")String key){changes.decide(order,id,d.approved(),d.note(),key);}
    @PostMapping("/{order}/changes/{id}/withdraw") void withdraw(@PathVariable UUID order,@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){changes.withdraw(order,id,key);}
    @GetMapping("/{order}/incidents") Object incidents(@PathVariable UUID order){return incidents.list(order);}
    @PostMapping("/{order}/incidents") Object report(@PathVariable UUID order,@RequestBody RentalIncidentApplication.Report report,@RequestHeader("Idempotency-Key")String key){return Map.of("id",incidents.report(order,report,key));}
    @PostMapping("/{order}/incidents/{id}/decide") void decideIncident(@PathVariable UUID order,@PathVariable UUID id,@RequestBody Decision d,@RequestHeader("Idempotency-Key")String key){incidents.decide(order,id,d.approved(),d.note(),key);}
}
