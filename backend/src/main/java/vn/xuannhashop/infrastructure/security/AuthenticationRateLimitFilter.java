package vn.xuannhashop.infrastructure.security;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.xuannhashop.infrastructure.persistence.Sql;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

/** Shared DB buckets, not a JVM lock. Never trust a raw client Forwarded header here. */
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private final Sql sql;
    public AuthenticationRateLimitFilter(Sql sql){this.sql=sql;}
    private boolean allow(String bucket,int limit){
        var row=sql.one("insert into authentication_rate_limit(bucket,window_start,hits) values(:b,clock_timestamp(),1) on conflict(bucket) do update set hits=case when authentication_rate_limit.window_start<clock_timestamp()-interval '1 minute' then 1 else authentication_rate_limit.hits+1 end,window_start=case when authentication_rate_limit.window_start<clock_timestamp()-interval '1 minute' then clock_timestamp() else authentication_rate_limit.window_start end returning hits",p("b",bucket));
        return ((Number)row.get("hits")).intValue()<=limit;
    }
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        String path=req.getRequestURI().substring(req.getContextPath().length());
        if(!req.getMethod().equals("POST")||!(path.equals("/api/auth/login")||path.equals("/api/auth/register")||path.equals("/api/auth/google/complete"))){chain.doFilter(req,res);return;}
        if(req.getContentLengthLong()>16384){res.sendError(413);return;}
        try{
            String ip=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(req.getRemoteAddr().getBytes(StandardCharsets.UTF_8)));
            sql.update("delete from authentication_rate_limit where window_start<clock_timestamp()-interval '1 day'",Map.of());
            if(!allow("global",200)||!allow("ip:"+ip,10)){res.setStatus(429);res.setHeader("Retry-After","60");res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"Quá nhiều lần đăng nhập/đăng ký. Vui lòng thử lại sau một phút.\"}");return;}
        }catch(Exception e){res.setStatus(503);return;}
        chain.doFilter(req,res);
    }
}
