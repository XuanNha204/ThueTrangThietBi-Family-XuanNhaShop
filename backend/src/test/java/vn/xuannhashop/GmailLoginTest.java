package vn.xuannhashop;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.jobs-enabled=false", "app.gmail.enabled=true", "app.google.enabled=true",
    "app.gmail.otp-key=test-key-for-gmail-integration-tests-only"
})
@AutoConfigureMockMvc
class GmailLoginTest {
    private static final String AUTH = "/api/auth/";
    private static final String PASSWORD = "Example!200";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired Mailbox mailbox;

    @Test
    void onlyOneAccountPerVerifiedGmailAndEachLoginRequiresBothSteps() throws Exception {
        String inbox = GmailLoginTest.inbox();
        String canonical = inbox + "@gmail.com";
        String alias = "TEST." + inbox.substring(4) + "+shop@GMAIL.COM";
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(AUTH + "options")).andExpect(jsonPath("$.gmail").value(true))
            .andExpect(jsonPath("$.google").value(false));
        mvc.perform(get(AUTH + "authorize/google")).andExpect(status().isNotFound());
        submit("register", session, registration(inbox + "@example.test")).andExpect(status().isBadRequest());
        assertThat(mailbox.codes.get(canonical)).isNull();

        String before = session.getId();
        submit("register", session, registration(alias)).andExpect(status().isOk())
            .andExpect(jsonPath("$.stage").value("otp")).andExpect(jsonPath("$.expiresIn").value(300));
        assertThat(session.getId()).isNotEqualTo(before);
        assertThat(accountCount(canonical)).isZero();
        mvc.perform(get(AUTH + "me").session(session)).andExpect(status().isUnauthorized());

        var verified = submit("otp/verify", session, Map.of("code", code(canonical)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER")).andReturn();
        String id = json.readTree(verified.getResponse().getContentAsString()).get("id").asText();
        assertThat(accountCount(canonical)).isEqualTo(1);
        submit("otp/verify", session, Map.of("code", code(canonical))).andExpect(status().isBadRequest());
        submit("register", new MockHttpSession(), registration(canonical)).andExpect(status().isConflict());

        MockHttpSession later = new MockHttpSession();
        submit("login", later, Map.of("username", alias, "password", "Wrong!234"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(AUTH + "otp/state").session(later)).andExpect(jsonPath("$.stage").value("none"));
        allowAnotherMail();
        submit("login", later, Map.of("username", alias, "password", PASSWORD))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("otp"));
        mvc.perform(get(AUTH + "me").session(later)).andExpect(status().isUnauthorized());
        submit("otp/verify", later, Map.of("code", code(canonical)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(AUTH + "me").session(later)).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void attemptsExpiryResendAndCancelAreEnforcedServerSide() throws Exception {
        String email = inbox() + "@gmail.com";
        MockHttpSession session = new MockHttpSession();
        submit("register", session, registration(email)).andExpect(status().isOk());
        var challenge = db.queryForObject("select id from gmail_login_challenge order by expires_at desc limit 1", UUID.class);
        mvc.perform(get(AUTH + "otp/state").session(session))
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.stage").value("otp"));
        submit("otp/resend", session, Map.of()).andExpect(status().isTooManyRequests());
        String actual = code(email);
        String wrong = "000000".equals(actual) ? "000001" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            submit("otp/verify", session, Map.of("code", wrong)).andExpect(status().isBadRequest());
        }
        assertThat(db.queryForObject("select attempts_left from gmail_login_challenge where id=?", Integer.class, challenge)).isZero();
        submit("otp/verify", session, Map.of("code", actual)).andExpect(status().isBadRequest());
        db.update("update gmail_login_challenge set expires_at=now()-interval '1 second', attempts_left=5 where id=?", challenge);
        submit("otp/verify", session, Map.of("code", actual)).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select attempts_left from gmail_login_challenge where id=?", Integer.class, challenge)).isEqualTo(5);
        submit("otp/cancel", session, Map.of()).andExpect(status().isNoContent());
        submit("otp/verify", session, Map.of("code", actual)).andExpect(status().isBadRequest());
        assertThat(accountCount(email)).isZero();
    }

    @Test
    void legacyCustomerRetainsProfileAndStaffCanStillUseUsername() throws Exception {
        String local = inbox();
        UUID account = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        db.update("insert into customer(id,name,phone,address) values (?, 'Khách cũ', '0901111111', 'Hồ sơ cũ')", customer);
        db.update("insert into user_account(id, username, password_hash, display_name, role, customer_id) values (?, ?, ?, 'Khách cũ', 'CUSTOMER', ?)",
            account, local, passwords.encode(PASSWORD), customer);
        String email = inbox() + "@gmail.com";
        MockHttpSession legacy = new MockHttpSession();
        submit("login", legacy, Map.of("username", local, "password", PASSWORD))
            .andExpect(jsonPath("$.stage").value("gmail"));
        mvc.perform(get(AUTH + "me").session(legacy)).andExpect(status().isUnauthorized());
        submit("otp/enroll", legacy, Map.of("email", email)).andExpect(jsonPath("$.stage").value("otp"));
        submit("otp/verify", legacy, Map.of("code", code(email)))
            .andExpect(jsonPath("$.id").value(account.toString()))
            .andExpect(jsonPath("$.customerId").value(customer.toString()));
        submit("login", new MockHttpSession(), Map.of("username", local, "password", PASSWORD))
            .andExpect(status().isBadRequest());

        String staff = inbox();
        db.update("insert into user_account(id, username, password_hash, display_name, role) values (?, ?, ?, 'Chủ cửa hàng', 'OWNER')",
            UUID.randomUUID(), staff, passwords.encode(PASSWORD));
        MockHttpSession staffSession = new MockHttpSession();
        submit("login", staffSession, Map.of("username", staff, "password", PASSWORD))
            .andExpect(jsonPath("$.role").value("OWNER"));
        mvc.perform(get(AUTH + "me").session(staffSession)).andExpect(jsonPath("$.username").value(staff));
    }

    private ResultActions submit(String path, MockHttpSession session, Map<String, String> input) throws Exception {
        return mvc.perform(post(AUTH + path).session(session).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(input)));
    }

    private Map<String, String> registration(String email) {
        return Map.of("email", email, "password", PASSWORD, "name", "Khách kiểm thử",
            "phone", "0901234567", "address", "Địa chỉ kiểm thử");
    }

    private static String inbox() {
        return "test" + UUID.randomUUID().toString().replace("-", "");
    }

    private String code(String email) {
        String code = mailbox.codes.get(email);
        assertThat(code).matches("[0-9]{6}");
        return code;
    }

    private int accountCount(String email) {
        return db.queryForObject("select count(*) from user_account where login_gmail=?", Integer.class, email);
    }

    private void allowAnotherMail() {
        db.update("update gmail_mail_limit set last_sent=now()-interval '61 seconds'");
    }

    @TestConfiguration
    static class MailFixture {
        @Bean Mailbox mailbox() { return new Mailbox(); }
    }

    static class Mailbox implements GmailCodeSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        public void send(String email, String code) { codes.put(email, code); }
    }
}
