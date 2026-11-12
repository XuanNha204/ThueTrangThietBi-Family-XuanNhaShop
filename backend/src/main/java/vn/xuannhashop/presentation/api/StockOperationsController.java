package vn.xuannhashop.presentation.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.inventory.application.StockOperationsApplication;
import vn.xuannhashop.business.inventory.application.StockOperationsApplication.*;
@RestController @RequestMapping("/api/stock")
public class StockOperationsController {
    private final StockOperationsApplication app;public StockOperationsController(StockOperationsApplication app){this.app=app;}
    record Warehouse(String name,String address) {}record Decision(boolean accepted,String note) {}
    @GetMapping Object overview(){return app.overview();}
    @PostMapping("/warehouses") Object warehouse(@RequestBody Warehouse c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.warehouse(c.name(),c.address(),key));}
    @PostMapping("/receipts") Object receipt(@RequestBody Receipt c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.receive(c,key));}
    @PostMapping("/receipts/{id}/decide") void receipt(@PathVariable UUID id,@RequestBody Decision c,@RequestHeader("Idempotency-Key")String key){app.decideReceipt(id,c.accepted(),c.note(),key);}
    @PostMapping("/actions") Object action(@RequestBody Action c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.action(c,key));}
    @PostMapping("/actions/{id}/decide") void action(@PathVariable UUID id,@RequestBody Decision c,@RequestHeader("Idempotency-Key")String key){app.decideAction(id,c.accepted(),c.note(),key);}
    @PostMapping("/counts") Object count(@RequestBody Count c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.count(c,key));}
    @PostMapping("/counts/{id}/decide") void count(@PathVariable UUID id,@RequestBody Decision c,@RequestHeader("Idempotency-Key")String key){app.decideCount(id,c.accepted(),c.note(),key);}
}
