package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class SupabaseAuthClientTests {

    private static final String ISSUER = "https://project.supabase.co/auth/v1";
    private static final String PUBLISHABLE_KEY = "test-publishable-key";
    private static final String ACCESS_TOKEN = "signed-access-token";
    private static final String REFRESH_TOKEN = "refresh-token-1";
    private static final String SUBJECT = "subject-123";

    @Test
    void signsInWithPasswordUsingTheExistingUserEndpointAndPublicKey() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(ISSUER + "/token?grant_type=password"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", PUBLISHABLE_KEY))
                .andExpect(content().json("""
                        {"email":"user@example.com","password":"sensitive-password"}
                        """))
                .andRespond(withSuccess(sessionJson(ACCESS_TOKEN, REFRESH_TOKEN), MediaType.APPLICATION_JSON));

        SupabaseAuthClient.SupabaseAuthSession session = client(restTemplate)
                .signInWithPassword(" user@example.com ", "sensitive-password");

        assertThat(session.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(session.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(session.subject()).isEqualTo(SUBJECT);
        server.verify();
    }

    @Test
    void exchangesTheRefreshTokenAndReturnsTheRotatedPair() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(ISSUER + "/token?grant_type=refresh_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", PUBLISHABLE_KEY))
                .andExpect(content().json("""
                        {"refresh_token":"refresh-token-1"}
                        """))
                .andRespond(withSuccess(
                        sessionJson("rotated-access-token", "refresh-token-2"),
                        MediaType.APPLICATION_JSON));

        SupabaseAuthClient.SupabaseAuthSession session = client(restTemplate).refresh(REFRESH_TOKEN);

        assertThat(session.accessToken()).isEqualTo("rotated-access-token");
        assertThat(session.refreshToken()).isEqualTo("refresh-token-2");
        assertThat(session.subject()).isEqualTo(SUBJECT);
        server.verify();
    }

    @Test
    void mapsCredentialRejectionWithoutPreservingTheProviderResponse() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(ISSUER + "/token?grant_type=password"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"msg\":\"invalid login credentials\"}"));

        assertThatThrownBy(() -> client(restTemplate)
                .signInWithPassword("user@example.com", "wrong-password"))
                .isInstanceOf(SupabaseAuthClient.CredentialsRejectedException.class)
                .hasMessage(null);
        server.verify();
    }

    @Test
    void mapsTimeoutToProviderFailureWithoutExposingTheRequest() throws Exception {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(ISSUER + "/token?grant_type=password"))
                .andRespond(MockRestResponseCreators.withException(
                        new SocketTimeoutException("provider timed out")));

        assertThatThrownBy(() -> client(restTemplate)
                .signInWithPassword("user@example.com", "sensitive-password"))
                .isInstanceOf(SupabaseAuthClient.ProviderUnavailableException.class)
                .hasMessage(null);
        server.verify();
    }

    @Test
    void rejectsAnIncompleteTokenResponse() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(ISSUER + "/token?grant_type=password"))
                .andRespond(withSuccess(
                        """
                        {"access_token":"access-only","user":{"id":"subject-123"}}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(restTemplate)
                .signInWithPassword("user@example.com", "sensitive-password"))
                .isInstanceOf(SupabaseAuthClient.InvalidAuthResponseException.class)
                .hasMessage(null);
        server.verify();
    }

    @Test
    void signsOutOnlyTheCurrentSupabaseSession() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(ISSUER + "/logout?scope=local"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", PUBLISHABLE_KEY))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client(restTemplate).signOutLocal(ACCESS_TOKEN);

        server.verify();
    }

    private static RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new SupabaseJwkSetApiKeyInterceptor(PUBLISHABLE_KEY));
        return restTemplate;
    }

    private static SupabaseAuthClient client(RestTemplate restTemplate) {
        return new SupabaseAuthClient(restTemplate, ISSUER);
    }

    private static String sessionJson(String accessToken, String refreshToken) {
        return """
                {
                  "access_token": "%s",
                  "refresh_token": "%s",
                  "token_type": "bearer",
                  "user": {"id": "%s"}
                }
                """.formatted(accessToken, refreshToken, SUBJECT);
    }
}
