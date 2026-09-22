package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

class SupabaseAuthenticatedIdentityAdapterTests {

    private static final String USER_ENDPOINT =
            "https://project.supabase.co/auth/v1/user";
    private static final String PUBLISHABLE_KEY = "test-publishable-key";
    private static final AccessTokenContext CONTEXT = new AccessTokenContext(
            ExternalSubject.fromSupabase("user-123"),
            "access-token");

    @Test
    void loadsVerifiedProfileUsingOnlySupabaseTrustedFields() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(USER_ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", PUBLISHABLE_KEY))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "user-123",
                          "email": " User@Example.COM ",
                          "email_confirmed_at": "2026-09-22T10:00:00Z",
                          "user_metadata": {"email": "attacker@example.com"}
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var profile = adapter(restTemplate).loadVerifiedProfile(CONTEXT);
        AuthenticatedIdentityProfile loaded = profile.orElseThrow();

        assertThat(loaded.subject()).isEqualTo(ExternalSubject.fromSupabase("user-123"));
        assertThat(loaded.email()).isEqualTo(NormalizedEmail.from("user@example.com"));
        assertThat(loaded.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void ignoresUserMetadataWhenTrustedEmailIsMissing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(USER_ENDPOINT))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "user-123",
                          "user_metadata": {"email": "attacker@example.com"}
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(adapter(restTemplate).loadVerifiedProfile(CONTEXT)).isEmpty();
        server.verify();
    }

    @Test
    void rejectsUnconfirmedEmail() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(USER_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"id\":\"user-123\",\"email\":\"user@example.com\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter(restTemplate).loadVerifiedProfile(CONTEXT)).isEmpty();
        server.verify();
    }

    @Test
    void rejectsResponseForDifferentSubject() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(USER_ENDPOINT))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "another-user",
                          "email": "user@example.com",
                          "email_confirmed_at": "2026-09-22T10:00:00Z"
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(adapter(restTemplate).loadVerifiedProfile(CONTEXT)).isEmpty();
        server.verify();
    }

    @Test
    void failsClosedForHttpErrors() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(USER_ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(adapter(restTemplate).loadVerifiedProfile(CONTEXT)).isEmpty();
        server.verify();
    }

    @Test
    void failsClosedForTimeouts() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(USER_ENDPOINT))
                .andRespond(withException(new SocketTimeoutException("timeout")));

        assertThat(adapter(restTemplate).loadVerifiedProfile(CONTEXT)).isEmpty();
        server.verify();
    }

    @Test
    void doesNotCallSupabaseForAContextFromAnotherProvider() {
        RestTemplate restTemplate = new RestTemplate();
        var adapter = adapter(restTemplate);
        var context = new AccessTokenContext(
                new ExternalSubject("OTHER_PROVIDER", "user-123"),
                "access-token");

        assertThat(adapter.loadVerifiedProfile(context)).isEmpty();
    }

    private static SupabaseAuthenticatedIdentityAdapter adapter(RestTemplate restTemplate) {
        return new SupabaseAuthenticatedIdentityAdapter(
                restTemplate,
                USER_ENDPOINT,
                PUBLISHABLE_KEY);
    }
}
