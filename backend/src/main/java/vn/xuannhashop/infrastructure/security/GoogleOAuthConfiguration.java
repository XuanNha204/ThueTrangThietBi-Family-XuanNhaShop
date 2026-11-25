package vn.xuannhashop.infrastructure.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.google.enabled", havingValue = "true")
class GoogleOAuthConfiguration {
    static final String START_BASE = "/api/auth/authorize";
    static final String CALLBACK = "/api/auth/google/callback";

    @Bean
    ClientRegistrationRepository googleRegistration(
            @Value("${app.google.client-id}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret,
            @Value("${app.google.redirect-uri}") URI redirectUri) {
        if (!clientId.matches("[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com") || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth requires a web client ID and secret.");
        }
        boolean loopback = "localhost".equals(redirectUri.getHost()) || "127.0.0.1".equals(redirectUri.getHost());
        if (redirectUri.getHost() == null || !CALLBACK.equals(redirectUri.getRawPath())
                || redirectUri.getUserInfo() != null || redirectUri.getQuery() != null || redirectUri.getFragment() != null
                || !("https".equals(redirectUri.getScheme()) || (loopback && "http".equals(redirectUri.getScheme())))) {
            throw new IllegalStateException("Google OAuth requires an HTTPS redirect URI at " + CALLBACK + ".");
        }
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
            .issuerUri("https://accounts.google.com")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .scope("openid", "email", "profile")
            .redirectUri(redirectUri.toASCIIString())
            .build());
    }

    @Bean
    GoogleOAuthLogin googleOAuthLogin(ClientRegistrationRepository registrations,
            @Qualifier("googleLoginController") AuthenticationSuccessHandler success,
            @Qualifier("googleLoginController") AuthenticationFailureHandler failure) {
        return new GoogleOAuthLogin(registrations, success, failure);
    }

    record GoogleOAuthLogin(ClientRegistrationRepository registrations, AuthenticationSuccessHandler success,
                            AuthenticationFailureHandler failure) {
        void configure(HttpSecurity http) throws Exception {
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, START_BASE);
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce().andThen(
                builder -> builder.additionalParameters(params -> params.put("prompt", "select_account"))));
            var oidc = new OidcUserService();
            oidc.setRetrieveUserInfo(request -> false);
            http.oauth2Login(oauth -> oauth
                .clientRegistrationRepository(registrations)
                .authorizedClientRepository(new DiscardGoogleTokens())
                .loginPage("/login")
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK))
                .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient()))
                .userInfoEndpoint(endpoint -> endpoint.oidcUserService(oidc))
                .successHandler(success)
                .failureHandler(failure)
                .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                    @Override
                    public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                        // Override the chain repository after Spring configures this filter.
                        filter.setSecurityContextRepository(new NullSecurityContextRepository());
                        return filter;
                    }
                }));
        }

        private RestClientAuthorizationCodeTokenResponseClient tokenClient() {
            var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
            requests.setReadTimeout(Duration.ofSeconds(10));
            var client = new RestClientAuthorizationCodeTokenResponseClient();
            client.setRestClient(RestClient.builder().requestFactory(requests)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
            return client;
        }
    }

    /** Google credentials are used once to sign in. Only a local shop session survives the callback. */
    private static class DiscardGoogleTokens implements OAuth2AuthorizedClientRepository {
        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId, Authentication principal,
                                                                        HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal,
                                         HttpServletRequest request, HttpServletResponse response) {}

        @Override
        public void removeAuthorizedClient(String registrationId, Authentication principal,
                                           HttpServletRequest request, HttpServletResponse response) {}
    }
}
