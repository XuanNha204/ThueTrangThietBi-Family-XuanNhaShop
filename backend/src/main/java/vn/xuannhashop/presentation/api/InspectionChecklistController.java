package vn.xuannhashop.presentation.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.maintenance.application.InspectionChecklistApplication;
@RestController
@RequestMapping("/api/maintenance/checklists/{type}")
public class InspectionChecklistController {
    private final InspectionChecklistApplication checklist;public InspectionChecklistController(InspectionChecklistApplication checklist){this.checklist=checklist;}
    @GetMapping Object current(@PathVariable UUID type){return checklist.current(type);}
    @GetMapping("/history") Object history(@PathVariable UUID type){return checklist.history(type);}
    @PostMapping Object publish(@PathVariable UUID type,@RequestBody InspectionChecklistApplication.Publish input,@RequestHeader("Idempotency-Key")String key){return Map.of("id",checklist.publish(type,input,key));}
}
