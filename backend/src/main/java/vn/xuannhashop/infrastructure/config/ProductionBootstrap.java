package vn.xuannhashop.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.infrastructure.persistence.Sql;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Component @Profile("prod")
@ConditionalOnProperty(name="app.bootstrap.enabled",havingValue="true")
public class ProductionBootstrap implements CommandLineRunner {
    private final Sql sql; private final PasswordEncoder encoder; private final Environment env;
    public ProductionBootstrap(Sql sql,PasswordEncoder encoder,Environment env){this.sql=sql;this.encoder=encoder;this.env=env;}
    @Transactional public void run(String... args){
        sql.one("select pg_advisory_xact_lock(684239110)",Map.of());
        if (((Number)sql.one("select count(*) n from user_account",Map.of()).get("n")).longValue()!=0) return;
        String username=env.getRequiredProperty("app.bootstrap.username"),password=env.getRequiredProperty("bootstrap.password"),name=env.getRequiredProperty("app.bootstrap.name"),address=env.getRequiredProperty("app.bootstrap.address"),warehouse=env.getRequiredProperty("app.bootstrap.warehouse");
        if(!username.matches("[a-z0-9_.-]{4,60}")||password.length()<20||password.getBytes(StandardCharsets.UTF_8).length>72||password.contains("XuanNhaDev")||name.isBlank()||name.length()>120||address.isBlank()||address.length()>400||warehouse.isBlank()||warehouse.length()>120)
            throw new IllegalStateException("Invalid production bootstrap settings; use a unique 20+ character password and shop address");
        UUID id=UUID.randomUUID();
        sql.update("insert into user_account(id,username,password_hash,display_name,role,enabled) values(:id,:u,:p,:n,'OWNER',true)",p("id",id,"u",username,"p",encoder.encode(password),"n",name));
        sql.update("insert into warehouse(id,name,address) values(:id,:n,:a)",p("id",UUID.randomUUID(),"n",warehouse,"a",address));
        sql.update("insert into salary_policy_version(id,name,description) values('DEV-HOURLY-1','Lương giờ và khoản đã duyệt','Cần cơ sở phê duyệt chính sách thực tế trước sử dụng.') on conflict do nothing",Map.of());
        sql.update("insert into audit_entry(id,actor,action,target_id,detail,created_at) values(:id,'SYSTEM','PRODUCTION_BOOTSTRAPPED',:owner,'Created first owner and warehouse; no sample transactions',now())",p("id",UUID.randomUUID(),"owner",id));
    }
}
