package vn.xuannhashop.presentation.api;
import java.util.Map;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.IdentityApplication;
import vn.xuannhashop.business.identity.application.GmailAddress;
import vn.xuannhashop.business.shared.DomainException;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;private final LoginSessions sessions;private final IdentityApplication identity;private final PasswordEncoder encoder;private final boolean googleEnabled;
    private final GmailLoginFlow gmail;
    AuthController(AuthenticationManager manager, LoginSessions sessions, IdentityApplication identity,
                   PasswordEncoder encoder, @Value("${app.google.enabled:false}") boolean googleEnabled,
                   ObjectProvider<GmailLoginFlow> gmail) {
        this.manager=manager;this.sessions=sessions;this.identity=identity;this.encoder=encoder;
        this.gmail=gmail.getIfAvailable();this.googleEnabled=googleEnabled;
    }
    record Login(@NotBlank @Size(max=160)String username,@NotBlank @Size(max=100)String password) {}
    record Register(@Size(max=60)String username,@Size(max=160)String email,@NotBlank @Size(min=8,max=72)String password,
                    @NotBlank @Size(max=120)String name,@NotBlank @Pattern(regexp="\\+?[0-9]{8,15}")String phone,
                    @NotBlank @Size(max=400)String address) {}
    @GetMapping("/csrf") Map<String,String> token(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/options") Map<String,Boolean> options(){return Map.of("google",googleEnabled,"gmail",gmail!=null);}
    @PostMapping("/login") Object login(@RequestBody @Valid Login input,HttpServletRequest req,HttpServletResponse res){
        String identifier=input.username();
        if (gmail!=null && identifier.contains("@")) identifier=GmailAddress.required(identifier);
        var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(identifier,input.password()));
        var recognized=identity.loginIdentity(auth.getName());
        if (sessions.passwordVersion(auth)!=recognized.credentialVersion()) throw new BadCredentialsException("Thông tin xác thực đã thay đổi.");
        if ("CUSTOMER".equals(recognized.account().role())) {
            if (gmail!=null) return gmail.login(recognized,identifier,req,res);
            if (recognized.gmail()!=null) throw new DomainException(503,"Xác minh Gmail chưa sẵn sàng. Vui lòng thử lại sau.");
        }
        if (gmail!=null) gmail.cancel(req);
        sessions.start(auth,req,res);return identity.me();
    }
    @PostMapping("/register") Object register(@RequestBody @Valid Register input,HttpServletRequest req,HttpServletResponse res){
        vn.xuannhashop.business.identity.application.PasswordPolicy.validate(input.password());
        String hash=encoder.encode(input.password());
        if (gmail!=null) return gmail.registration(input.email(),new GmailLoginFlow.Contact(hash,input.name(),input.phone(),input.address()),req,res);
        return Map.of("id",identity.register(input.username(),hash,input.name(),input.phone(),input.address()));
    }
    @GetMapping("/me") Object me(){return identity.me();}
    @GetMapping("/profile") Object profile(){return identity.profile();}
    @PostMapping("/profile") void profile(@RequestBody IdentityApplication.Profile p){identity.updateProfile(p);}
}
