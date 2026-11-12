package vn.xuannhashop.presentation.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.AccountAdministrationApplication;
@RestController
@RequestMapping("/api/accounts")
public class AccountAdministrationController {
    private final AccountAdministrationApplication accounts;
    public AccountAdministrationController(AccountAdministrationApplication accounts){this.accounts=accounts;}
    record RoleInput(String expectedRole,String role,String reason) {}
    @PostMapping Object create(@RequestBody AccountAdministrationApplication.CreateStaff c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",accounts.createStaff(c,key));}
    @PostMapping("/{id}/role") void role(@PathVariable UUID id,@RequestBody RoleInput c,@RequestHeader("Idempotency-Key")String key){accounts.changeRole(id,c.expectedRole(),c.role(),c.reason(),key);}
}
