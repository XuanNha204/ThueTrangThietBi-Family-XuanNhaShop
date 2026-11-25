package vn.xuannhashop.presentation.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.AccountPasswordService;

@RestController
@RequestMapping("/api/auth/password")
class PasswordController {
    private final AccountPasswordService passwords;
    private final LoginSessions sessions;

    PasswordController(AccountPasswordService passwords, LoginSessions sessions) {
        this.passwords = passwords;
        this.sessions = sessions;
    }

    record Change(@NotBlank @Size(max = 100) String currentPassword,
                  @NotBlank @Size(min = 8, max = 72) String newPassword,
                  @NotBlank @Size(min = 8, max = 72) String confirmPassword) {}

    @GetMapping
    ResponseEntity<AccountPasswordService.Options> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(passwords.options());
    }

    @PostMapping("/change")
    ResponseEntity<Void> change(@Valid @RequestBody Change input, HttpServletRequest req) {
        passwords.change(input.currentPassword(), input.newPassword(), input.confirmPassword(), sessions.version(req));
        sessions.end(req);
        return ResponseEntity.noContent().build();
    }
}
