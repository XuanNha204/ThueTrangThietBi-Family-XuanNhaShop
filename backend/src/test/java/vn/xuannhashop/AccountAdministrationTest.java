package vn.xuannhashop;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.xuannhashop.infrastructure.config.DevSeed;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.jobs-enabled=false"})
@AutoConfigureMockMvc
class AccountAdministrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;
    String key(){return UUID.randomUUID().toString();}
    record Created(UUID id,String username) {}
    Created create(String role)throws Exception{
        String name="staff_"+key().substring(0,8),operation=key();String body=json.writeValueAsString(Map.of("username",name,"password","JunitOnly!Password123","displayName","JUNIT staff","role",role));
        var result=mvc.perform(post("/api/accounts").with(user("owner")).with(csrf()).header("Idempotency-Key",operation).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
        UUID id=UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        mvc.perform(post("/api/accounts").with(user("owner")).with(csrf()).header("Idempotency-Key",operation).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));return new Created(id,name);
    }
    @Test void ownerCreatesHashedStaffOnceAndNoPasswordAppearsInAuditOrApi()throws Exception{
        var c=create("INVENTORY");String hash=db.queryForObject("select password_hash from user_account where id=?",String.class,c.id());assertThat(encoder.matches("JunitOnly!Password123",hash)).isTrue();assertThat(hash).startsWith("$2");
        assertThat(db.queryForObject("select count(*) from user_account where username=?",Integer.class,c.username())).isEqualTo(1);
        mvc.perform(get("/api/accounts").with(user("owner"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(hash))));
        assertThat(db.queryForList("select detail from audit_entry where target_id=?",String.class,c.id()).toString()).doesNotContain("JunitOnly!Password123",hash);
    }
    @Test void roleChangeAppliesToExistingAuthenticatedRequestsAndStaleRoleCannotOverwrite()throws Exception{
        var c=create("INVENTORY");mvc.perform(get("/api/finance/ledger").with(user(c.username()))).andExpect(status().isForbidden());
        String body=json.writeValueAsString(Map.of("expectedRole","INVENTORY","role","ACCOUNTANT","reason","JUNIT thay nhiệm vụ"));
        mvc.perform(post("/api/accounts/"+c.id()+"/role").with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/finance/ledger").with(user(c.username()))).andExpect(status().isOk());
        mvc.perform(post("/api/accounts/"+c.id()+"/role").with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isConflict());
    }
    @Test void customerCannotGrantPrivilegesAndCsrfIsRequired()throws Exception{
        String body=json.writeValueAsString(Map.of("username","junit_forbidden","password","JunitOnly!Password123","displayName","JUNIT","role","OWNER"));
        mvc.perform(post("/api/accounts").with(user("customer")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts").with(user("owner")).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        var c=create("INVENTORY");mvc.perform(post("/api/accounts/"+c.id()+"/role").with(user(c.username())).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content("{\"expectedRole\":\"INVENTORY\",\"role\":\"OWNER\",\"reason\":\"Tự nâng quyền\"}")).andExpect(status().isForbidden());
    }
    @Test void ownerCannotDemoteSelfOrPromoteCustomerAndInvalidStaffInputDoesNotWrite()throws Exception{
        UUID owner=db.queryForObject("select id from user_account where username='owner'",UUID.class),customer=db.queryForObject("select id from user_account where username='customer'",UUID.class);
        mvc.perform(post("/api/accounts/"+owner+"/role").with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content("{\"expectedRole\":\"OWNER\",\"role\":\"HR\",\"reason\":\"Không tự hạ quyền\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts/"+customer+"/role").with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content("{\"expectedRole\":\"CUSTOMER\",\"role\":\"OWNER\",\"reason\":\"Không nâng khách\"}")).andExpect(status().isBadRequest());
        for(String password:List.of("short","á".repeat(40)))mvc.perform(post("/api/accounts").with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(json.writeValueAsString(Map.of("username","staff_"+key().substring(0,8),"password",password,"displayName","JUNIT","role","HR")))).andExpect(status().isBadRequest());
    }
}
