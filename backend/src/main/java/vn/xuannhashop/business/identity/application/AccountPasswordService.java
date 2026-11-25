package vn.xuannhashop.business.identity.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class AccountPasswordService {
    private final IdentityRepository identities;
    private final ActorContext actor;
    private final PasswordHashing hashing;
    private final ActivityService audit;

    public AccountPasswordService(IdentityRepository identities, ActorContext actor, PasswordHashing hashing,
                                  ActivityService audit) {
        this.identities = identities;
        this.actor = actor;
        this.hashing = hashing;
        this.audit = audit;
    }

    public record Options(boolean hasPassword, boolean verifiedGmail) {}
    public record RecoveryTarget(UUID id, long version) {
        String challengeScope() { return id + ":" + version; }
    }

    public Options options() {
        String username = actor.current().username();
        var credentials = identities.find(username);
        var identity = identities.loginIdentity(username);
        return new Options(credentials.passwordHash() != null, identity.gmail() != null);
    }

    @Transactional
    public void change(String current, String newPassword, String confirmPassword, long sessionVersion) {
        validateNew(newPassword, confirmPassword);
        var account = actor.current();
        var credentials = identities.find(account.username());
        if (credentials == null || !credentials.account().enabled() || credentials.credentialVersion() != sessionVersion) {
            throw new DomainException(401, "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
        }
        if (credentials.passwordHash() == null) {
            throw DomainException.bad("Hãy xác minh Gmail để đặt mật khẩu cho tài khoản.");
        }
        if (!hashing.matches(current, credentials.passwordHash())) {
            throw DomainException.bad("Mật khẩu hiện tại chưa đúng.");
        }
        if (current.equals(newPassword)) throw DomainException.bad("Mật khẩu mới phải khác mật khẩu hiện tại.");
        persist(account.id(), sessionVersion, newPassword);
        audit.record("ACCOUNT_PASSWORD_CHANGED", account.id(), "Đổi mật khẩu tài khoản.");
    }

    public RecoveryTarget recoveryTarget(String gmail) {
        var credentials = identities.findByLoginGmail(gmail);
        if (credentials == null || !credentials.account().enabled() || !"CUSTOMER".equals(credentials.account().role())) return null;
        return new RecoveryTarget(credentials.account().id(), credentials.credentialVersion());
    }

    @Transactional
    public void recover(RecoveryTarget target, String newPassword) {
        PasswordPolicy.validate(newPassword);
        persist(target.id(), target.version(), newPassword);
        audit.system("ACCOUNT_PASSWORD_RECOVERED", target.id(), "Đặt lại mật khẩu qua Gmail đã xác minh.");
    }

    public static void validateNew(String newPassword, String confirmPassword) {
        PasswordPolicy.validate(newPassword);
        if (!newPassword.equals(confirmPassword)) throw DomainException.bad("Hai mật khẩu mới chưa khớp.");
    }

    private void persist(UUID id, long version, String newPassword) {
        if (!identities.updatePassword(id, version, hashing.encode(newPassword))) {
            throw DomainException.conflict("Thông tin xác thực đã thay đổi. Hãy bắt đầu lại.");
        }
    }
}
