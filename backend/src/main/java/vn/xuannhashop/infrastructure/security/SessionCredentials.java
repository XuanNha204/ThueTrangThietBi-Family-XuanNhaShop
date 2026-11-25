package vn.xuannhashop.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.identity.application.IdentityRepository.Credentials;
import vn.xuannhashop.presentation.api.SessionVersion;

@Component
final class SessionCredentials implements SessionVersion {
    private static final String KEY = SessionCredentials.class.getName();

    @Override
    public long current(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute(KEY) instanceof Long version ? version : 0L;
    }

    @Override
    public void save(HttpServletRequest request, long version) {
        request.getSession().setAttribute(KEY, version);
    }

    @Override
    public long authenticated(Authentication authentication) {
        return ((PasswordUser) authentication.getPrincipal()).version;
    }

    static final class PasswordUser extends User {
        private final long version;

        PasswordUser(Credentials credentials) {
            super(credentials.account().username(), credentials.passwordHash(), credentials.account().enabled(), true, true,
                true, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + credentials.account().role())));
            version = credentials.credentialVersion();
        }
    }
}
