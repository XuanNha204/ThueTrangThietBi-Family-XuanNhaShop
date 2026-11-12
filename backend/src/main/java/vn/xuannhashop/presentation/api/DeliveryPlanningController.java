package vn.xuannhashop.presentation.api;
import java.util.*;import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.delivery.application.DeliveryPlanningApplication;
@RestController public class DeliveryPlanningController {
    private final DeliveryPlanningApplication app;public DeliveryPlanningController(DeliveryPlanningApplication app){this.app=app;}
    record Ack(UUID guideId,String confirmedBy,String evidence){}record Finish(boolean completed,String outcome){}
    @GetMapping("/api/catalog/{id}/guide") Object guide(@PathVariable UUID id){return app.guide(id);}
    @GetMapping("/api/dispatch") Object overview(){return app.overview();}
    @PostMapping("/api/dispatch/guides") Object publish(@RequestBody DeliveryPlanningApplication.Guide c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.publish(c,key));}
    @GetMapping("/api/rentals/{id}/guides") Object guides(@PathVariable UUID id){return app.orderGuides(id);}
    @PostMapping("/api/rentals/{id}/guides/acknowledge") Object acknowledge(@PathVariable UUID id,@RequestBody Ack c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.acknowledge(id,c.guideId(),c.confirmedBy(),c.evidence(),key));}
    @PostMapping("/api/dispatch/assignments") Object assign(@RequestBody DeliveryPlanningApplication.Assignment c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.assign(c,key));}
    @PostMapping("/api/dispatch/assignments/{id}/finish") void finish(@PathVariable UUID id,@RequestBody Finish c,@RequestHeader("Idempotency-Key")String key){app.finish(id,c.completed(),c.outcome(),key);}
}
