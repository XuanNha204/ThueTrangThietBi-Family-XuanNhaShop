package vn.xuannhashop;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.xuannhashop.business.identity.application.GmailCodeSender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.jobs-enabled=false", "app.gmail.enabled=true", "app.gmail.otp-key=test-only-password-recovery-signing-key"
})
@AutoConfigureMockMvc
class AccountPasswordTest {
    private static final String AUTH = "/api/auth/";
    private static final String OLD = "Sample!Old14";
    private static final String NEW = "Sample!New25";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @Autowired Mailbox mailbox;

    @Test
    void changeVerifiesCurrentPasswordAndInvalidatesExistingSessions() throws Exception {
        String username = uniqueName();
        db.update("""
            insert into user_account(id,username,password_hash,display_name,role)
            values (?, ?, ?, 'Nhân viên kiểm thử', 'OWNER')
            """, UUID.randomUUID(), username, encoder.encode(OLD));
        var current = new MockHttpSession();
        var other = new MockHttpSession();
        login(current, username, OLD).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OWNER"));
        login(other, username, OLD).andExpect(status().isOk());
        mvc.perform(get(AUTH + "password").session(current))
            .andExpect(jsonPath("$.hasPassword").value(true)).andExpect(jsonPath("$.verifiedGmail").value(false));
        send("password/change", new MockHttpSession(), change(OLD, NEW, NEW)).andExpect(status().isUnauthorized());
        mvc.perform(post(AUTH + "password/change").session(current)
            .contentType("application/json").content(json.writeValueAsString(change(OLD, NEW, NEW))))
            .andExpect(status().isForbidden());
        send("password/change", current, change("Wrong!123", NEW, NEW)).andExpect(status().isBadRequest());
        send("password/change", current, change(OLD, OLD, OLD)).andExpect(status().isBadRequest());
        send("password/change", current, change(OLD, NEW, OLD)).andExpect(status().isBadRequest());
        send("password/change", current, change(OLD, NEW, NEW)).andExpect(status().isNoContent());
        assertThat(current.isInvalid()).isTrue();
        mvc.perform(get(AUTH + "me").session(other)).andExpect(status().isUnauthorized());
        login(new MockHttpSession(), username, OLD).andExpect(status().isUnauthorized());
        var fresh = new MockHttpSession();
        login(fresh, username, NEW).andExpect(status().isOk());
        mvc.perform(get(AUTH + "me").session(fresh)).andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void recoveryUsesOneTimeCodeRevokesPendingLoginAndKeepsOtpRequired() throws Exception {
        String local = uniqueName();
        String email = local + "@gmail.com";
        String alias = "TEST." + local.substring(4) + "+recovery@GMAIL.COM";
        customer(email, OLD);

        var signedIn = new MockHttpSession();
        login(signedIn, email, OLD).andExpect(jsonPath("$.stage").value("otp"));
        send("otp/verify", signedIn, Map.of("code", mailbox.loginCodes.get(email))).andExpect(status().isOk());
        db.update("update gmail_mail_limit set last_sent=now()-interval '61 seconds'");
        var unfinished = new MockHttpSession();
        login(unfinished, email, OLD).andExpect(jsonPath("$.stage").value("otp"));
        String previousCode = mailbox.loginCodes.get(email);

        var reset = new MockHttpSession();
        var unknown = new MockHttpSession();
        String unknownEmail = uniqueName() + "@gmail.com";
        var knownResponse = start(reset, alias).andExpect(status().isAccepted()).andReturn().getResponse();
        var unknownResponse = start(unknown, unknownEmail).andExpect(status().isAccepted()).andReturn().getResponse();
        assertThat(knownResponse.getContentAsString()).isEqualTo(unknownResponse.getContentAsString());
        String code = recoveryCode(email);
        assertThat(mailbox.recoveryCodes).doesNotContainKey(unknownEmail);
        String invalid = "000000".equals(code) ? "000001" : "000000";
        send("password/recovery/complete", reset, recovery(invalid, NEW)).andExpect(status().isBadRequest());
        send("password/recovery/complete", new MockHttpSession(), recovery(code, NEW)).andExpect(status().isBadRequest());
        send("password/recovery/complete", reset, recovery(code, NEW)).andExpect(status().isNoContent());
        assertThat(reset.isInvalid()).isTrue();
        send("password/recovery/complete", new MockHttpSession(), recovery(code, NEW)).andExpect(status().isBadRequest());
        mvc.perform(get(AUTH + "me").session(signedIn)).andExpect(status().isUnauthorized());
        send("otp/verify", unfinished, Map.of("code", previousCode)).andExpect(status().isBadRequest());
        login(new MockHttpSession(), email, OLD).andExpect(status().isUnauthorized());
        db.update("update gmail_mail_limit set last_sent=now()-interval '61 seconds'");
        var fresh = new MockHttpSession();
        login(fresh, email, NEW).andExpect(jsonPath("$.stage").value("otp"));
        mvc.perform(get(AUTH + "me").session(fresh)).andExpect(status().isUnauthorized());
        send("otp/verify", fresh, Map.of("code", mailbox.loginCodes.get(email))).andExpect(status().isOk());
        mvc.perform(get(AUTH + "me").session(fresh)).andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void verifiedGoogleUserCanSetFirstPasswordAndExpiredRecoveryCodeFails() throws Exception {
        String email = uniqueName() + "@gmail.com";
        UUID account = customer(email, null);
        db.update("insert into google_customer_identity(subject,account_id) values (?,?)", uniqueName(), account);
        var expired = new MockHttpSession();
        start(expired, email).andExpect(status().isAccepted());
        String previous = recoveryCode(email);
        UUID challenge = db.queryForObject("select id from gmail_login_challenge order by expires_at desc limit 1", UUID.class);
        db.update("update gmail_login_challenge set expires_at=now()-interval '1 second' where id=?", challenge);
        send("password/recovery/complete", expired, recovery(previous, NEW)).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select password_hash from user_account where id=?", String.class, account)).isNull();
        db.update("update gmail_mail_limit set last_sent=now()-interval '61 seconds'");
        mailbox.recoveryCodes.remove(email);
        var reset = new MockHttpSession();
        start(reset, email).andExpect(status().isAccepted());
        send("password/recovery/complete", reset, recovery(recoveryCode(email), NEW)).andExpect(status().isNoContent());
        assertThat(db.queryForObject("select credential_version from user_account where id=?", Long.class, account)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from google_customer_identity where account_id=?", Integer.class, account)).isEqualTo(1);
        login(new MockHttpSession(), email, NEW).andExpect(jsonPath("$.stage").value("otp"));
    }

    private ResultActions login(MockHttpSession session, String username, String password) throws Exception {
        return send("login", session, Map.of("username", username, "password", password));
    }

    private ResultActions start(MockHttpSession session, String email) throws Exception {
        return send("password/recovery/start", session, Map.of("email", email));
    }

    private ResultActions send(String path, MockHttpSession session, Map<String, String> body) throws Exception {
        return mvc.perform(post(AUTH + path).session(session).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(body)));
    }

    private Map<String, String> change(String current, String next, String confirmation) {
        return Map.of("currentPassword", current, "newPassword", next, "confirmPassword", confirmation);
    }

    private Map<String, String> recovery(String code, String password) {
        return Map.of("code", code, "newPassword", password, "confirmPassword", password);
    }

    private String recoveryCode(String email) {
        await().atMost(Duration.ofSeconds(4)).until(() -> mailbox.recoveryCodes.containsKey(email));
        return mailbox.recoveryCodes.get(email);
    }

    private String uniqueName() {
        return "test" + UUID.randomUUID().toString().replace("-", "");
    }

    private UUID customer(String gmail, String password) {
        UUID customer = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        db.update("insert into customer(id,name,phone,address) values (?, 'Khách thử mật khẩu', '0901234567', 'Địa chỉ kiểm thử')", customer);
        db.update("""
            insert into user_account(id,username,password_hash,display_name,role,customer_id,login_gmail)
            values (?, ?, ?, 'Khách thử mật khẩu', 'CUSTOMER', ?, ?)
            """, account, uniqueName(), password == null ? null : encoder.encode(password), customer, gmail);
        return account;
    }

    @TestConfiguration
    static class Fixture {
        @Bean Mailbox mailbox() { return new Mailbox(); }
    }

    static class Mailbox implements GmailCodeSender {
        final Map<String, String> loginCodes = new ConcurrentHashMap<>();
        final Map<String, String> recoveryCodes = new ConcurrentHashMap<>();
        public void send(String recipient, String code) { loginCodes.put(recipient, code); }
        public void sendRecovery(String recipient, String code) { recoveryCodes.put(recipient, code); }
    }
}
