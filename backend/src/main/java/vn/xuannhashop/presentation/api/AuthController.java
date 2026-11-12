package vn.xuannhashop.presentation.api;
import java.util.Map;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.identity.application.IdentityApplication;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;private final SecurityContextRepository contexts;private final SessionAuthenticationStrategy strategy;private final IdentityApplication identity;private final PasswordEncoder encoder;private final CsrfTokenRepository csrf;
    public AuthController(AuthenticationManager m,SecurityContextRepository c,SessionAuthenticationStrategy s,IdentityApplication i,PasswordEncoder e,CsrfTokenRepository csrf){manager=m;contexts=c;strategy=s;identity=i;encoder=e;this.csrf=csrf;}
    record Login(@NotBlank @Size(max=80)String username,@NotBlank @Size(max=100)String password) {}
    record Register(@NotBlank @Size(max=60)String username,@NotBlank @Size(min=8,max=72)String password,@NotBlank @Size(max=120)String name,@NotBlank @Size(max=20)String phone,@NotBlank @Size(max=400)String address) {}
    @GetMapping("/csrf") Map<String,String> token(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @PostMapping("/login") Object login(@RequestBody @Valid Login input,HttpServletRequest req,HttpServletResponse res){var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.username(),input.password()));strategy.onAuthentication(auth,req,res);var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);csrf.saveToken(null,req,res);return identity.me();}
    @PostMapping("/register") Object register(@RequestBody @Valid Register input){vn.xuannhashop.business.identity.application.PasswordPolicy.validate(input.password());return Map.of("id",identity.register(input.username(),encoder.encode(input.password()),input.name(),input.phone(),input.address()));}
    @GetMapping("/me") Object me(){return identity.me();}
    @GetMapping("/profile") Object profile(){return identity.profile();}
    @PostMapping("/profile") void profile(@RequestBody IdentityApplication.Profile p){identity.updateProfile(p);}
}
