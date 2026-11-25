package vn.xuannhashop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.jobs-enabled=false", "app.google.enabled=true",
    "app.google.client-id=1234-test.apps.googleusercontent.com",
    "app.google.client-secret=fixture-only",
    "app.google.redirect-uri=https://mywebapp.id.vn/api/auth/google/callback"
})
@AutoConfigureMockMvc
class GoogleLoginTest {
    private static final String CALLBACK = "https://mywebapp.id.vn/api/auth/google/callback";
    private static final String TASK = "/api/auth/google";
    private static final String CLIENT = "1234-test.apps.googleusercontent.com";
    private static final String SECRET = "fixture-only";
    private static final String DETAILS = """
        {"name":"Khách Google","phone":"0901234567","address":"Địa chỉ liên hệ thử nghiệm"}
        """;
    private static final FakeProvider PROVIDER = new FakeProvider();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;

    @AfterAll
    static void stopProvider() {
        PROVIDER.server.stop(0);
    }

    @Test
    void firstSignInCompletesCustomerWithoutLinkingAnotherAccountAndNextSignInUsesSubject() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = subject + "@example.test";
        UUID original = UUID.randomUUID();
        String legacyUsername = "local-" + original;
        db.update("insert into customer(id,name,phone,email,address) values (?, 'Khách cũ', '0901111111', ?, 'Hồ sơ cũ')",
            original, email);
        db.update("insert into user_account(id, username, password_hash, display_name, role, customer_id) values (?, ?, ?, 'Khách cũ', 'CUSTOMER', ?)",
            UUID.randomUUID(), legacyUsername, passwords.encode("Example!200"), original);

        mvc.perform(get("/api/auth/options")).andExpect(jsonPath("$.google").value(true));
        var first = begin();
        accept(first, subject, email, true, first.query().get("nonce"), "/login?google=setup");
        mvc.perform(get("/api/auth/me").session(first.session())).andExpect(status().isUnauthorized());
        mvc.perform(get(TASK + "/pending").session(first.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        mvc.perform(post(TASK + "/complete").session(first.session()).contentType("application/json").content(DETAILS))
            .andExpect(status().isForbidden());
        mvc.perform(post(TASK + "/complete").session(first.session()).with(csrf()).contentType("application/json")
            .content(DETAILS.replace("0901234567", "abc")))
            .andExpect(status().isBadRequest());
        String initialSessionId = first.session().getId();
        var response = mvc.perform(post(TASK + "/complete").session(first.session()).with(csrf()).contentType("application/json")
            .content(DETAILS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andReturn().getResponse();
        var account = json.readTree(response.getContentAsString());
        String accountId = account.get("id").asText();
        String customerId = account.get("customerId").asText();
        assertThat(customerId).isNotEqualTo(original.toString());
        assertThat(first.session().getId()).isNotEqualTo(initialSessionId);
        assertThat(db.queryForObject("select password_hash from user_account where id=?", String.class, UUID.fromString(accountId)))
            .isNull();
        mvc.perform(get(TASK + "/pending").session(first.session())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/me").session(first.session())).andExpect(jsonPath("$.id").value(accountId));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username", legacyUsername, "password", "Example!200"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(original.toString()));

        var again = begin();
        accept(again, subject, "new-" + email, true, again.query().get("nonce"), "/login?google=success");
        mvc.perform(get("/api/auth/me").session(again.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(accountId));
        mvc.perform(get("/api/auth/profile").session(again.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/accounts").session(again.session())).andExpect(status().isForbidden());

        db.update("update user_account set enabled=false where id=?", UUID.fromString(accountId));
        var locked = begin();
        accept(locked, subject, email, true, locked.query().get("nonce"), "/login?google=blocked");
        mvc.perform(get("/api/auth/me").session(locked.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void missingStateCannotRedeemCodeAndUnverifiedEmailOrWrongNonceCannotCreateSession() throws Exception {
        var flow = begin();
        int before = PROVIDER.calls.get();
        mvc.perform(get(CALLBACK).param("state", "unrelated-state").param("code", "code-with-wrong-state")
            .session(flow.session()))
            .andExpect(redirectedUrl("/login?google=failed"));
        assertThat(PROVIDER.calls.get()).isEqualTo(before);
        var wrongNonce = begin();
        accept(wrongNonce, "wrong-nonce", "test@example.test", true, "wrong", "/login?google=failed");
        var unverified = begin();
        accept(unverified, "unverified-email", "test@example.test", false, unverified.query().get("nonce"),
            "/login?google=failed");

        assertThat(unverified.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        mvc.perform(get(TASK + "/pending").session(unverified.session())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/me").session(unverified.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void twoVerifiedSessionsForOneGoogleSubjectCreateOnlyOneAccount() throws Exception {
        var first = begin();
        var second = begin();
        String subject = UUID.randomUUID().toString();
        accept(first, subject, "google-sample@example.test", true, first.query().get("nonce"), "/login?google=setup");
        accept(second, subject, "google-sample@example.test", true, second.query().get("nonce"), "/login?google=setup");
        String account = json.readTree(mvc.perform(post(TASK + "/complete").session(first.session()).with(csrf())
            .contentType("application/json").content(DETAILS)).andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString()).get("id").asText();
        mvc.perform(post(TASK + "/complete").session(second.session()).with(csrf()).contentType("application/json")
            .content(DETAILS)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(account));
        assertThat(db.queryForObject("select count(*) from google_customer_identity where subject=?",
            Integer.class, subject)).isEqualTo(1);
    }

    private Flow begin() throws Exception {
        var started = mvc.perform(get("/api/auth/authorize/google"))
            .andExpect(status().isFound()).andReturn();
        var url = URI.create(started.getResponse().getRedirectedUrl());
        assertThat(url.getPath()).isEqualTo("/authorize");
        var query = parameters(url.getRawQuery());
        assertThat(query).containsEntry("redirect_uri", CALLBACK).containsEntry("code_challenge_method", "S256")
            .containsKeys("nonce", "state", "code_challenge");
        return new Flow((MockHttpSession) started.getRequest().getSession(false), query);
    }

    private void accept(Flow flow, String subject, String email, boolean verified, String nonce, String landing)
            throws Exception {
        String code = UUID.randomUUID().toString();
        PROVIDER.grants.put(code, new Grant(subject, email, verified, nonce, flow.query().get("code_challenge")));
        mvc.perform(get(CALLBACK).param("state", flow.query().get("state")).param("code", code).session(flow.session()))
            .andExpect(status().isFound()).andExpect(redirectedUrl(landing));
        assertThat(PROVIDER.grants).doesNotContainKey(code);
    }

    private static Map<String, String> parameters(String form) {
        var values = new HashMap<String, String>();
        for (String field : form.split("&")) {
            String[] pair = field.split("=", 2);
            values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    record Flow(MockHttpSession session, Map<String, String> query) {}
    record Grant(String subject, String email, boolean verified, String nonce, String challenge) {}

    @TestConfiguration
    static class ProviderConfiguration {
        @Bean
        @Primary
        ClientRegistrationRepository fixtureRegistration() {
            String issuer = PROVIDER.issuer();
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
                .clientId(CLIENT).clientSecret(SECRET).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(CALLBACK)
                .scope("openid", "email", "profile").clientName("Google test fixture")
                .issuerUri(issuer).authorizationUri(issuer + "/authorize").tokenUri(issuer + "/token")
                .jwkSetUri(issuer + "/keys").build());
        }
    }

    private static class FakeProvider {
        final HttpServer server;
        final RSAKey key;
        final Map<String, Grant> grants = new ConcurrentHashMap<>();
        final AtomicInteger calls = new AtomicInteger();

        FakeProvider() {
            try {
                key = new RSAKeyGenerator(2048).keyID("fixture").generate();
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/keys", exchange -> reply(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
                server.createContext("/token", this::token);
                server.start();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        String issuer() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void token(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            try {
                var form = parameters(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var grant = grants.remove(form.get("code"));
                var digest = MessageDigest.getInstance("SHA-256");
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    digest.digest(form.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
                if (grant == null || !CLIENT.equals(form.get("client_id")) || !SECRET.equals(form.get("client_secret"))
                        || !CALLBACK.equals(form.get("redirect_uri")) || !challenge.equals(grant.challenge())) {
                    reply(exchange, 400, "{\"error\":\"invalid_grant\"}");
                    return;
                }
                Instant now = Instant.now();
                var claims = new JWTClaimsSet.Builder().issuer(issuer()).audience(CLIENT).subject(grant.subject())
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("nonce", grant.nonce()).claim("email", grant.email()).claim("email_verified", grant.verified())
                    .claim("name", "Khách thử").build();
                var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("fixture").build(), claims);
                jwt.sign(new RSASSASigner(key));
                reply(exchange, 200, JSONObjectUtils.toJSONString(Map.of("id_token", jwt.serialize(), "access_token", "fixture-token",
                    "token_type", "Bearer", "expires_in", 300, "scope", "openid email profile")));
            } catch (Exception e) {
                reply(exchange, 400, "{\"error\":\"invalid_grant\"}");
            }
        }

        private void reply(HttpExchange exchange, int status, String text) throws IOException {
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
