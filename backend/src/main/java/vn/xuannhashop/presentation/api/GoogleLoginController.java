package vn.xuannhashop.presentation.api;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.bind.annotation.*;
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

    GoogleLoginController(IdentityApplication identity, LoginSessions sessions) {
        this.identity = identity;
        this.sessions = sessions;
    }

    record Contact(@NotBlank @Size(max = 120) String name,
                   @NotBlank @Size(max = 20) String phone,
                   @NotBlank @Size(max = 400) String address) {}

    private record Pending(GoogleProfile profile, Instant expiresAt) {}

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication result)
            throws IOException {
        var session = request.getSession();
        session.removeAttribute(PENDING);
        if (!(result.getPrincipal() instanceof OidcUser user) || !Boolean.TRUE.equals(user.getEmailVerified())
                || user.getEmail() == null || user.getEmail().isBlank() || user.getEmail().length() > 160) {
            redirect(response, "failed");
            return;
        }

        UserAccount account;
        try {
            account = identity.googleAccount(user.getSubject());
        } catch (DomainException e) {
            redirect(response, e.status() == 403 ? "blocked" : "failed");
            return;
        }
        sessions.finishOAuthLogin(account, request, response);
        if (account == null) {
            String name = user.getFullName();
            if (name == null || name.length() > 120) name = "";
            session.setAttribute(PENDING, new Pending(new GoogleProfile(user.getSubject(), user.getEmail(), name),
                Instant.now().plus(PENDING_LIFETIME)));
        }
        redirect(response, account == null ? "setup" : "success");
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
    UserAccount complete(@RequestBody @Valid Contact contact, HttpServletRequest request, HttpServletResponse response) {
        var profile = pendingProfile(request);
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
