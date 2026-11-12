package vn.xuannhashop.presentation.api;
import java.util.*;import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.CustomerVerificationApplication;
@RestController public class CustomerVerificationController {
    private final CustomerVerificationApplication app;public CustomerVerificationController(CustomerVerificationApplication app){this.app=app;}
    @GetMapping("/api/catalog/eligibility-policy") Object policy(){return app.publicPolicy();}
    @GetMapping("/api/verifications") Object overview(){return app.overview();}
    @PostMapping("/api/verifications/policy") Object policy(@RequestBody CustomerVerificationApplication.Policy c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.publish(c,key));}
    @PostMapping("/api/verifications") Object verify(@RequestBody CustomerVerificationApplication.Verification c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.verify(c,key));}
}
