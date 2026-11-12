package vn.xuannhashop.presentation.api;
import java.util.*;import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.payroll.application.PayrollExtrasApplication;
@RestController @RequestMapping("/api/payroll-extras") public class PayrollExtrasController {
    private final PayrollExtrasApplication app;public PayrollExtrasController(PayrollExtrasApplication app){this.app=app;}
    record Link(UUID staffId,UUID accountId){}record Decision(boolean accepted,String note){}record Question(UUID payrollId,String question){}record Answer(String answer){}
    @GetMapping Object overview(){return app.overview();}
    @GetMapping("/mine") Object mine(){return app.mine();}
    @PostMapping("/links") Object link(@RequestBody Link c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.link(c.staffId(),c.accountId(),key));}
    @PostMapping("/adjustments") Object adjustment(@RequestBody PayrollExtrasApplication.Adjustment c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.adjustment(c,key));}
    @PostMapping("/adjustments/{id}/decide") void decision(@PathVariable UUID id,@RequestBody Decision c,@RequestHeader("Idempotency-Key")String key){app.decide(id,c.accepted(),c.note(),key);}
    @PostMapping("/questions") Object question(@RequestBody Question c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",app.question(c.payrollId(),c.question(),key));}
    @PostMapping("/questions/{id}/answer") void answer(@PathVariable UUID id,@RequestBody Answer c,@RequestHeader("Idempotency-Key")String key){app.answer(id,c.answer(),key);}
}
