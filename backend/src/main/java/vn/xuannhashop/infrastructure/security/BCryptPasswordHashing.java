package vn.xuannhashop.infrastructure.security;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.xuannhashop.business.identity.application.PasswordHashing;
@Component
public class BCryptPasswordHashing implements PasswordHashing {
    private final PasswordEncoder encoder;
    public BCryptPasswordHashing(PasswordEncoder encoder){this.encoder=encoder;}
    public String encode(String password){return encoder.encode(password);}
    public boolean matches(String password, String hash){
        return password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72 && encoder.matches(password,hash);
    }
}
