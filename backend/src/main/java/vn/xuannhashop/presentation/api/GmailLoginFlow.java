package vn.xuannhashop.presentation.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.GmailAddress;
import vn.xuannhashop.business.identity.application.GmailChallengeService;
import vn.xuannhashop.business.identity.application.IdentityApplication;
import vn.xuannhashop.business.identity.domain.UserAccount;
import vn.xuannhashop.business.shared.DomainException;

@RestController
@RequestMapping("/api/auth/otp")
@ConditionalOnProperty(name = "app.gmail.enabled", havingValue = "true")
class GmailLoginFlow {
    private static final String KEY = GmailLoginFlow.class.getName() + ".pending";
    private final GmailChallengeService codes;
    private final IdentityApplication identity;
    private final LoginSessions sessions;

    GmailLoginFlow(GmailChallengeService codes, IdentityApplication identity, LoginSessions sessions) {
        this.codes = codes;
        this.identity = identity;
        this.sessions = sessions;
    }

    enum Action { LOGIN, REGISTER, ENROLL }
    record Status(String stage, String emailHint, long expiresIn, long resendAfter) {}
    record Verification(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}
    record Enrollment(@NotBlank @Size(max = 160) String email) {}
    record Contact(String passwordHash, String name, String phone, String address) {}
    private record Pending(UUID id, Action action, UUID account, String gmail, Contact contact, Instant deadline, Instant issuedAt) {}

    Status login(IdentityApplication.LoginIdentity account, String identifier,
                 HttpServletRequest req, HttpServletResponse res) {
        var recognized = account.account();
        Instant deadline = Instant.now().plusSeconds(900);
        if (account.gmail() == null) {
            return begin(new Pending(UUID.randomUUID(), Action.ENROLL, recognized.id(), null, null, deadline, null), req, res);
        }
        if (!account.gmail().equals(identifier)) {
            throw DomainException.bad("Hãy dùng Gmail đã xác minh để đăng nhập.");
        }
        return begin(new Pending(UUID.randomUUID(), Action.LOGIN, recognized.id(), account.gmail(), null, deadline, null), req, res);
    }

    Status registration(String email, Contact contact, HttpServletRequest req, HttpServletResponse res) {
        String gmail = GmailAddress.required(email);
        identity.ensureGmailAvailable(gmail);
        return begin(new Pending(UUID.randomUUID(), Action.REGISTER, null, gmail, contact,
            Instant.now().plusSeconds(900), null), req, res);
    }

    @GetMapping("/state")
    ResponseEntity<Status> current(HttpServletRequest req) {
        Pending pending = active(req);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            pending == null ? new Status("none", "", 0, 0) : status(pending));
    }

    @PostMapping("/enroll")
    Status enroll(@RequestBody @Valid Enrollment input, HttpServletRequest req) {
        Pending pending = required(req);
        if (pending.action() != Action.ENROLL || pending.gmail() != null) {
            throw DomainException.bad("Hãy đăng nhập lại bằng tài khoản cũ để xác minh Gmail.");
        }
        String email = GmailAddress.required(input.email());
        identity.ensureGmailAvailable(email);
        return store(issue(new Pending(UUID.randomUUID(), pending.action(), pending.account(), email, null, pending.deadline(), null)), req);
    }

    @PostMapping("/resend")
    Status resend(HttpServletRequest req) {
        Pending pending = required(req);
        if (pending.gmail() == null) throw DomainException.bad("Hãy nhập địa chỉ Gmail trước.");
        return store(issue(pending), req);
    }

    @PostMapping("/verify")
    UserAccount verify(@RequestBody @Valid Verification input, HttpServletRequest req, HttpServletResponse res) {
        Pending pending = required(req);
        if (pending.issuedAt() == null) throw DomainException.bad("Hãy yêu cầu gửi mã trước.");
        codes.verify(pending.id(), input.code());
        cancel(req);
        UserAccount account = switch (pending.action()) {
            case LOGIN -> identity.gmailLogin(pending.account(), pending.gmail());
            case ENROLL -> identity.enrollGmail(pending.account(), pending.gmail());
            case REGISTER -> {
                var contact = pending.contact();
                yield identity.registerGmail(pending.gmail(), contact.passwordHash(), contact.name(), contact.phone(), contact.address());
            }
        };
        sessions.start(account, req, res);
        return account;
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(HttpServletRequest req) {
        var session = req.getSession(false);
        if (session != null) session.removeAttribute(KEY);
    }

    private Status begin(Pending pending, HttpServletRequest req, HttpServletResponse res) {
        Pending challenge = pending.gmail() == null ? pending : issue(pending);
        sessions.beginPending(req, res);
        return store(challenge, req);
    }

    private Pending issue(Pending pending) {
        if (status(pending).resendAfter() > 0) throw new DomainException(429, "Chưa đến thời điểm gửi lại mã.");
        codes.send(pending.id(), pending.gmail());
        return new Pending(pending.id(), pending.action(), pending.account(), pending.gmail(), pending.contact(),
            pending.deadline(), Instant.now());
    }

    private Status store(Pending pending, HttpServletRequest req) {
        req.getSession().setAttribute(KEY, pending);
        return status(pending);
    }

    private Pending active(HttpServletRequest req) {
        var session = req.getSession(false);
        if (session == null || !(session.getAttribute(KEY) instanceof Pending pending)) return null;
        if (Instant.now().isBefore(pending.deadline())) return pending;
        session.removeAttribute(KEY);
        return null;
    }

    private Pending required(HttpServletRequest req) {
        Pending pending = active(req);
        if (pending == null) throw DomainException.bad("Phiên xác minh đã hết hạn. Hãy đăng nhập hoặc đăng ký lại.");
        return pending;
    }

    private Status status(Pending pending) {
        if (pending.issuedAt() == null) return new Status("gmail", "", 0, 0);
        long now = Instant.now().getEpochSecond();
        long sent = pending.issuedAt().getEpochSecond();
        long expires = Math.min(sent + GmailChallengeService.CODE_TTL_SECONDS, pending.deadline().getEpochSecond());
        return new Status("otp", pending.gmail().charAt(0) + "•••@gmail.com", Math.max(0, expires - now),
            Math.max(0, sent + GmailChallengeService.RESEND_SECONDS - now));
    }
}
