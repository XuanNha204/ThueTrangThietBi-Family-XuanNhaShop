package vn.xuannhashop.infrastructure.security;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.xuannhashop.business.identity.application.PasswordHashing;
@Component
public class BCryptPasswordHashing implements PasswordHashing {
    private final PasswordEncoder encoder;
    public BCryptPasswordHashing(PasswordEncoder encoder){this.encoder=encoder;}
    public String encode(String password){return encoder.encode(password);}
}
