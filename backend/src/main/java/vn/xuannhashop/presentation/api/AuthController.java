package vn.xuannhashop.presentation.api;
import java.util.Map;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.IdentityApplication;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;private final LoginSessions sessions;private final IdentityApplication identity;private final PasswordEncoder encoder;private final boolean googleEnabled;
    AuthController(AuthenticationManager manager, LoginSessions sessions, IdentityApplication identity,
                   PasswordEncoder encoder, @Value("${app.google.enabled:false}") boolean googleEnabled) {
        this.manager=manager;this.sessions=sessions;this.identity=identity;this.encoder=encoder;this.googleEnabled=googleEnabled;
    }
    record Login(@NotBlank @Size(max=80)String username,@NotBlank @Size(max=100)String password) {}
    record Register(@NotBlank @Size(max=60)String username,@NotBlank @Size(min=8,max=72)String password,@NotBlank @Size(max=120)String name,@NotBlank @Size(max=20)String phone,@NotBlank @Size(max=400)String address) {}
    @GetMapping("/csrf") Map<String,String> token(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/options") Map<String,Boolean> options(){return Map.of("google",googleEnabled);}
    @PostMapping("/login") Object login(@RequestBody @Valid Login input,HttpServletRequest req,HttpServletResponse res){
        var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.username(),input.password()));
        sessions.start(auth,req,res);return identity.me();
    }
    @PostMapping("/register") Object register(@RequestBody @Valid Register input){vn.xuannhashop.business.identity.application.PasswordPolicy.validate(input.password());return Map.of("id",identity.register(input.username(),encoder.encode(input.password()),input.name(),input.phone(),input.address()));}
    @GetMapping("/me") Object me(){return identity.me();}
    @GetMapping("/profile") Object profile(){return identity.profile();}
    @PostMapping("/profile") void profile(@RequestBody IdentityApplication.Profile p){identity.updateProfile(p);}
}
