package vn.xuannhashop.presentation.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.identity.domain.UserAccount;

@Component
class LoginSessions {
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy strategy;
    private final CsrfTokenRepository csrf;

    LoginSessions(SecurityContextRepository contexts, SessionAuthenticationStrategy strategy, CsrfTokenRepository csrf) {
        this.contexts = contexts;
        this.strategy = strategy;
        this.csrf = csrf;
    }

    void start(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        strategy.onAuthentication(authentication, request, response);
        save(authentication, request, response);
    }

    void start(UserAccount account, HttpServletRequest request, HttpServletResponse response) {
        start(localAuthentication(account), request, response);
    }

    void beginPending(HttpServletRequest request, HttpServletResponse response) {
        request.getSession();
        request.changeSessionId();
        save(null, request, response);
    }

    void finishOAuthLogin(UserAccount account, HttpServletRequest request, HttpServletResponse response) {
        // The OAuth authentication filter has already rotated the session ID.
        save(account == null ? null : localAuthentication(account), request, response);
    }

    private Authentication localAuthentication(UserAccount account) {
        var user = User.withUsername(account.username()).password("").roles(account.role()).build();
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }

    private void save(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(null, request, response);
    }
}
