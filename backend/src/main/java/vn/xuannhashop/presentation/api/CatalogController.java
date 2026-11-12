package vn.xuannhashop.presentation.api;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.rental.application.RentalApplication;
import vn.xuannhashop.business.pricing.application.PricingApplication;
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final EquipmentApplication equipment;private final RentalApplication rentals;private final PricingApplication pricing;
    public CatalogController(EquipmentApplication e,RentalApplication r,PricingApplication p){equipment=e;rentals=r;pricing=p;}
    @GetMapping Object catalog(@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return equipment.catalog(from,to);}
    @PostMapping("/quote") Object quote(@RequestBody RentalApplication.Create c){return rentals.quote(c);}
    @GetMapping("/policy") Object policy(){return pricing.policy();}
}
