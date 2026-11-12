package vn.xuannhashop.presentation.api;
import org.springframework.web.bind.annotation.*;import vn.xuannhashop.business.reporting.application.BusinessPerformanceApplication;
@RestController public class BusinessPerformanceController {
    private final BusinessPerformanceApplication app;public BusinessPerformanceController(BusinessPerformanceApplication app){this.app=app;}
    @GetMapping("/api/reports/performance") Object report(@RequestParam String month){return app.report(month);}
}
