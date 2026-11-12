package vn.xuannhashop.presentation.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.rental.application.RentalApplication;
@RestController
@RequestMapping("/api/rentals")
public class RentalController {
    private final RentalApplication rentals;public RentalController(RentalApplication r){rentals=r;}
    @GetMapping Object list(){return rentals.list();}
    @GetMapping("/{id}") Object detail(@PathVariable UUID id){return rentals.detail(id);}
    @PostMapping Object create(@RequestBody RentalApplication.Create c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",rentals.hold(c,key));}
    @PostMapping("/{id}/confirm") void confirm(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){rentals.confirm(id,key);}
    @PostMapping("/{id}/prepare") void prepare(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){rentals.prepare(id,key);}
    @PostMapping("/{id}/cancel") void cancel(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){rentals.cancel(id,key);}
    @PostMapping("/{id}/settle") void settle(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){rentals.settle(id,key);}
}
