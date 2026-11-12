package vn.xuannhashop.presentation.api;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.reporting.application.EquipmentHistoryApplication;
@RestController
public class EquipmentHistoryController {
    private final EquipmentHistoryApplication history;public EquipmentHistoryController(EquipmentHistoryApplication history){this.history=history;}
    @GetMapping("/api/equipment/{id}/history") Object history(@PathVariable UUID id,@RequestParam(required=false)Instant before,@RequestParam(required=false)String beforeKey){return history.history(id,before,beforeKey);}
}
