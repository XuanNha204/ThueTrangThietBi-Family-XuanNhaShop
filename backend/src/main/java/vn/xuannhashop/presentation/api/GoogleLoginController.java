package vn.xuannhashop.presentation.api;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.GmailAddress;
import vn.xuannhashop.business.identity.application.IdentityApplication;
import vn.xuannhashop.business.identity.application.IdentityApplication.GoogleProfile;
import vn.xuannhashop.business.identity.domain.UserAccount;
import vn.xuannhashop.business.shared.DomainException;

@RestController
@RequestMapping("/api/auth/google")
@ConditionalOnProperty(name = "app.google.enabled", havingValue = "true")
class GoogleLoginController implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private static final String PENDING = GoogleLoginController.class.getName() + ".pending";
    private static final Duration PENDING_LIFETIME = Duration.ofMinutes(10);

    private final IdentityApplication identity;
    private final LoginSessions sessions;
    private final GmailLoginFlow gmail;

    GoogleLoginController(IdentityApplication identity, LoginSessions sessions, ObjectProvider<GmailLoginFlow> gmail) {
        this.identity = identity;
        this.sessions = sessions;
        this.gmail = gmail.getIfAvailable();
    }

    record Contact(@NotBlank @Size(max = 120) String name,
                   @NotBlank @Pattern(regexp = "\\+?[0-9]{8,15}") String phone,
                   @NotBlank @Size(max = 400) String address) {}

    private record Pending(GoogleProfile profile, Instant expiresAt) {}

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication result)
            throws IOException {
        var session = request.getSession();
        session.removeAttribute(PENDING);
        if (gmail != null) {
            gmail.cancel(request);
            sessions.finishOAuthLogin(null, request, response);
        }
        if (!(result.getPrincipal() instanceof OidcUser user) || !Boolean.TRUE.equals(user.getEmailVerified())
                || user.getEmail() == null || user.getEmail().isBlank() || user.getEmail().length() > 160) {
            redirect(response, "failed");
            return;
        }

        String name = user.getFullName();
        if (name == null || name.length() > 120) name = "";
        var profile = new GoogleProfile(user.getSubject(), user.getEmail(), name);
        if (gmail != null) {
            requireGmailAndContinue(profile, request, response);
            return;
        }

        UserAccount account;
        try {
            account = identity.googleAccount(user.getSubject());
            if (account != null && identity.loginIdentity(account.username()).gmail() != null) {
                sessions.finishOAuthLogin(null, request, response);
                redirect(response, "mail-failed");
                return;
            }
        } catch (DomainException e) {
            redirect(response, e.status() == 403 ? "blocked" : "failed");
            return;
        }
        sessions.finishOAuthLogin(account, request, response);
        if (account == null) {
            rememberProfile(profile, request);
        }
        redirect(response, account == null ? "setup" : "success");
    }

    private void requireGmailAndContinue(GoogleProfile profile, HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        try {
            GmailAddress.required(profile.email());
        } catch (DomainException e) {
            redirect(res, "gmail-required");
            return;
        }
        try {
            var account = identity.googleGmailAccount(profile);
            if (account == null) {
                rememberProfile(profile, req);
                redirect(res, "setup");
            } else {
                gmail.google(profile, account, null, req, res);
                redirect(res, "otp");
            }
        } catch (DomainException e) {
            String state = switch (e.status()) {
                case 403 -> "blocked";
                case 429 -> "limited";
                case 503 -> "mail-failed";
                default -> "failed";
            };
            redirect(res, state);
        }
    }

    private void rememberProfile(GoogleProfile profile, HttpServletRequest request) {
        request.getSession().setAttribute(PENDING, new Pending(profile, Instant.now().plus(PENDING_LIFETIME)));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
            throws IOException {
        var session = request.getSession(false);
        if (session != null) session.removeAttribute(PENDING);
        redirect(response, "failed");
    }

    @GetMapping("/pending")
    ResponseEntity<Map<String, String>> pending(HttpServletRequest request) {
        var profile = pendingProfile(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("name", profile.name(), "email", profile.email()));
    }

    @PostMapping("/complete")
    Object complete(@RequestBody @Valid Contact contact, HttpServletRequest request, HttpServletResponse response) {
        var profile = pendingProfile(request);
        if (gmail != null) {
            var known = identity.googleGmailAccount(profile);
            var status = gmail.google(profile, known, new GmailLoginFlow.Contact(null, contact.name(), contact.phone(),
                contact.address()), request, response);
            request.getSession().removeAttribute(PENDING);
            return status;
        }
        var account = identity.registerGoogle(profile, contact.name(), contact.phone(), contact.address());
        request.getSession().removeAttribute(PENDING);
        sessions.start(account, request, response);
        return account;
    }

    private GoogleProfile pendingProfile(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(PENDING) instanceof Pending pending
                && Instant.now().isBefore(pending.expiresAt())) {
            return pending.profile();
        }
        if (session != null) session.removeAttribute(PENDING);
        throw DomainException.bad("Phiên xác thực Google đã hết hạn. Vui lòng đăng nhập bằng Google lại.");
    }

    private void redirect(HttpServletResponse response, String state) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.sendRedirect("/login?google=" + state);
    }
}
