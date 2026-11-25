package vn.xuannhashop.infrastructure.security;
import vn.xuannhashop.business.shared.security.ActorContext;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.xuannhashop.business.identity.application.*;
import vn.xuannhashop.business.identity.domain.UserAccount;
import vn.xuannhashop.business.shared.DomainException;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(11);}
    @Bean UserDetailsService users(IdentityRepository r){return name->{var c=name.contains("@")?r.findByLoginGmail(name):r.find(name);if(c==null||c.passwordHash()==null)throw new UsernameNotFoundException("Tài khoản không hợp lệ");return new SessionCredentials.PasswordUser(c);};}
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
    @Bean SecurityContextRepository contexts(){return new HttpSessionSecurityContextRepository();}
    @Bean SessionAuthenticationStrategy sessionStrategy(){return new ChangeSessionIdAuthenticationStrategy();}
    @Bean CsrfTokenRepository csrfTokens(){return new HttpSessionCsrfTokenRepository();}
    @Bean ActorContext actorContext(IdentityRepository r){return ()->{var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||auth.getName().equals("anonymousUser"))throw new DomainException(401,"Vui lòng đăng nhập.");var c=r.find(auth.getName());if(c==null||!c.account().enabled())throw new DomainException(401,"Phiên đăng nhập không còn hiệu lực.");return new vn.xuannhashop.business.shared.security.ActorSnapshot(c.account().id(),c.account().username(),c.account().displayName(),c.account().role(),c.account().customerId(),c.account().enabled());};}
    @Bean SecurityFilterChain chain(HttpSecurity http,SecurityContextRepository contexts,CsrfTokenRepository csrf,IdentityRepository identities,org.springframework.core.env.Environment env,vn.xuannhashop.infrastructure.persistence.Sql sql,SessionCredentials sessionCredentials,ObjectProvider<GoogleOAuthConfiguration.GoogleOAuthLogin> googleLogin)throws Exception{
        http.securityContext(c->c.securityContextRepository(contexts))
            .csrf(c->c.csrfTokenRepository(csrf).ignoringRequestMatchers(req->req.getMethod().equals("POST")&&req.getRequestURI().equals(req.getContextPath()+vn.xuannhashop.business.finance.application.SePayWebhookApplication.PATH)))
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,vn.xuannhashop.business.finance.application.SePayWebhookApplication.PATH).permitAll().requestMatchers("/api/auth/csrf","/api/auth/login","/api/auth/register","/api/catalog/**","/actuator/health","/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/options","/api/auth/otp/state",GoogleOAuthConfiguration.START_BASE + "/google",GoogleOAuthConfiguration.CALLBACK,"/api/auth/google/pending").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/google/complete","/api/auth/otp/enroll","/api/auth/otp/verify","/api/auth/otp/resend","/api/auth/otp/cancel","/api/auth/password/recovery/start","/api/auth/password/recovery/complete").permitAll()
                .requestMatchers("/api/equipment-types/*/photo", "/api/equipment-types/*/photo/*", "/actuator/**").hasRole("OWNER")
                .anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->json(res,401,"Vui lòng đăng nhập.")).accessDeniedHandler((req,res,ex)->json(res,403,"Không đủ quyền hoặc mã CSRF hết hạn. Hãy tải lại trang.")))
            .logout(l->l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("XUANNHA_SESSION").logoutSuccessHandler((req,res,a)->res.setStatus(204)))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .addFilterAfter(new OncePerRequestFilter(){
                protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
                    var a=SecurityContextHolder.getContext().getAuthentication();
                    if(a!=null&&a.isAuthenticated()&&!a.getName().equals("anonymousUser")){
                        var c=identities.find(a.getName());
                        if(c==null||!c.account().enabled()){SecurityContextHolder.clearContext();var session=req.getSession(false);if(session!=null)session.invalidate();json(res,401,"Tài khoản đã bị khóa.");return;}
                        if(c.credentialVersion()!=sessionCredentials.current(req)){
                            SecurityContextHolder.clearContext();
                            var session=req.getSession(false);
                            if(session!=null)session.invalidate();
                            json(res,401,"Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
                            return;
                        }
                        var u=User.withUsername(c.account().username()).password("").roles(c.account().role()).build();
                        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u,null,u.getAuthorities()));
                    }
                    chain.doFilter(req,res);
                }
            },SecurityContextHolderFilter.class);
        if(env.getProperty("app.auth-rate-limit",Boolean.class,false)) http.addFilterBefore(new AuthenticationRateLimitFilter(sql),CsrfFilter.class);
        var google = googleLogin.getIfAvailable();
        if (google != null) google.configure(http);
        return http.build();
    }
    private static void json(HttpServletResponse res,int status,String message)throws IOException{res.setStatus(status);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\""+message+"\"}");}
}
