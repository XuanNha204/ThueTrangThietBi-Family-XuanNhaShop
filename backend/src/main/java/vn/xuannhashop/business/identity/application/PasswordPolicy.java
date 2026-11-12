package vn.xuannhashop.business.identity.application;

import java.nio.charset.StandardCharsets;
import vn.xuannhashop.business.shared.DomainException;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void validate(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("(?s).*[A-Z].*")
                || !password.matches("(?s).*[a-z].*")
                || !password.matches("(?s).*[0-9].*")
                || !password.matches("(?s).*[^\\p{L}\\p{N}\\s].*")) {
            throw DomainException.bad("Mật khẩu cần ít nhất 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt; tối đa 72 byte UTF-8.");
        }
    }
}
