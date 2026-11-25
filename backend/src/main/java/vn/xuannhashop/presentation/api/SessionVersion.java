package vn.xuannhashop.presentation.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/** Connects the credential revision checked at login with the one stored in the HTTP session. */
public interface SessionVersion {
    long current(HttpServletRequest request);
    long authenticated(Authentication authentication);
    void save(HttpServletRequest request, long version);
}
