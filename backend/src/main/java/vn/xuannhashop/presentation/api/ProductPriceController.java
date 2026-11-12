package vn.xuannhashop.presentation.api;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.pricing.application.ProductPriceApplication;
@RestController
@RequestMapping("/api/catalog/{id}/price")
public class ProductPriceController {
    private final ProductPriceApplication prices;public ProductPriceController(ProductPriceApplication prices){this.prices=prices;}
    @GetMapping("/history") Object history(@PathVariable UUID id){return prices.history(id);}
    @PostMapping Object change(@PathVariable UUID id,@RequestBody ProductPriceApplication.Change c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",prices.change(id,c,key));}
}
