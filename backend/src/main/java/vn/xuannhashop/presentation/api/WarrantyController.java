package vn.xuannhashop.presentation.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.maintenance.application.WarrantyApplication;
@RestController @RequestMapping("/api/warranty") public class WarrantyController {
    private final WarrantyApplication app;public WarrantyController(WarrantyApplication app){this.app=app;}
    record Claim(UUID equipmentId,String problem,String partnerReference){}record Result(String outcome,String result){}
    @GetMapping Object overview(){return app.overview();}
    @PostMapping("/coverage") Object coverage(@RequestBody WarrantyApplication.Coverage c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.coverage(c,key));}
    @PostMapping("/cases") Object claim(@RequestBody Claim c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.open(c.equipmentId(),c.problem(),c.partnerReference(),key));}
    @PostMapping("/cases/{id}/close") void close(@PathVariable UUID id,@RequestBody Result c,@RequestHeader("Idempotency-Key")String key){app.close(id,c.outcome(),c.result(),key);}
}
