package vn.xuannhashop.presentation.api;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.AccountPasswordService;
import vn.xuannhashop.business.identity.application.GmailAddress;
import vn.xuannhashop.business.identity.application.GmailChallengeService;
import vn.xuannhashop.business.shared.DomainException;

@RestController
@RequestMapping("/api/auth/password/recovery")
@ConditionalOnProperty(name = "app.gmail.enabled", havingValue = "true")
class PasswordRecoveryController {
    private static final String KEY = PasswordRecoveryController.class.getName() + ".pending";
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PasswordRecoveryController.class);
    private static final String BAD_CODE = "Mã không đúng, đã hết hạn hoặc hết lượt thử. Hãy kiểm tra lại hoặc yêu cầu mã mới.";
    private final AccountPasswordService passwords;
    private final GmailChallengeService codes;
    private final Executor worker;
    private final LoginSessions sessions;

    PasswordRecoveryController(AccountPasswordService passwords, GmailChallengeService codes, LoginSessions sessions,
                               @Qualifier("recoveryMailWorker") Executor worker) {
        this.passwords = passwords;
        this.codes = codes;
        this.sessions = sessions;
        this.worker = worker;
    }

    record Start(@NotBlank @Size(max = 160) String email) {}
    record Started(int resendAfter) {}
    record Complete(@NotBlank @Pattern(regexp = "[0-9]{6}") String code,
                    @NotBlank @Size(min = 8, max = 72) String newPassword,
                    @NotBlank @Size(min = 8, max = 72) String confirmPassword) {}
    private record Pending(UUID id, String gmail, Instant deadline, Instant resendAt) {}

    @PostMapping("/start")
    ResponseEntity<Started> start(@Valid @RequestBody Start input, HttpServletRequest request) {
        String gmail = GmailAddress.required(input.email());
        Instant now = Instant.now();
        var session = request.getSession();
        var previous = session.getAttribute(KEY) instanceof Pending pending && pending.gmail().equals(gmail)
            && now.isBefore(pending.deadline()) ? pending : null;
        if (previous != null && now.isBefore(previous.resendAt())) {
            int wait = (int) (previous.resendAt().getEpochSecond() - now.getEpochSecond());
            return accepted(Math.max(1, wait));
        }
        var pending = new Pending(previous == null ? UUID.randomUUID() : previous.id(), gmail,
            previous == null ? now.plusSeconds(900) : previous.deadline(),
            now.plusSeconds(GmailChallengeService.RESEND_SECONDS));
        try {
            worker.execute(() -> deliver(pending));
        } catch (RejectedExecutionException full) {
            throw new DomainException(503, "Hệ thống đang bận. Vui lòng thử lại trong ít phút.");
        }
        session.setAttribute(KEY, pending);
        return accepted(GmailChallengeService.RESEND_SECONDS);
    }

    private ResponseEntity<Started> accepted(int wait) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new Started(wait));
    }

    private void deliver(Pending pending) {
        try {
            codes.sendRecovery(pending.id(), pending.gmail(), passwords.recoveryTarget(pending.gmail()));
        } catch (DomainException e) {
            if (e.status() == 503) LOG.warn("Recovery email delivery unavailable.");
        } catch (RuntimeException e) {
            LOG.warn("Password recovery delivery failed ({})", e.getClass().getSimpleName());
        }
    }

    @PostMapping("/complete")
    ResponseEntity<Void> complete(@Valid @RequestBody Complete input, HttpServletRequest request) {
        AccountPasswordService.validateNew(input.newPassword(), input.confirmPassword());
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(KEY) instanceof Pending pending)
                || !Instant.now().isBefore(pending.deadline())) {
            throw DomainException.bad(BAD_CODE);
        }
        var target = passwords.recoveryTarget(pending.gmail());
        try {
            codes.verifyRecovery(pending.id(), target, input.code());
        } catch (DomainException e) {
            throw DomainException.bad(BAD_CODE);
        }
        if (target == null) throw DomainException.bad(BAD_CODE);
        passwords.recover(target, input.newPassword());
        sessions.end(request);
        return ResponseEntity.noContent().build();
    }
}
