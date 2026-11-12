package vn.xuannhashop.presentation.api;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.maintenance.application.MaintenancePlanningApplication;
@RestController
@RequestMapping("/api/maintenance/plans")
public class MaintenancePlanningController {
    private final MaintenancePlanningApplication maintenance;
    public MaintenancePlanningController(MaintenancePlanningApplication maintenance){this.maintenance=maintenance;}
    record Schedule(UUID equipmentId,Instant startsAt,Instant endsAt,String reason) {}
    record Reason(String reason) {}
    @GetMapping Object plans(){return maintenance.plans();}
    @PostMapping Object schedule(@RequestBody Schedule c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",maintenance.schedule(c.equipmentId(),c.startsAt(),c.endsAt(),c.reason(),key));}
    @PostMapping("/{id}/start") void start(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){maintenance.start(id,key);}
    @PostMapping("/{id}/cancel") void cancel(@PathVariable UUID id,@RequestBody Reason c,@RequestHeader("Idempotency-Key")String key){maintenance.cancel(id,c.reason(),key);}
    @PostMapping("/{id}/complete") void complete(@PathVariable UUID id,@RequestBody vn.xuannhashop.business.maintenance.application.InspectionChecklistApplication.Input c,@RequestHeader("Idempotency-Key")String key){maintenance.complete(id,c,key);}
}
