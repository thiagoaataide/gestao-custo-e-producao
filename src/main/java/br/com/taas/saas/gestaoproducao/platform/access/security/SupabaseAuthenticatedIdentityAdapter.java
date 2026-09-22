package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.application.port.out.AuthenticatedIdentityPort;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

/**
 * Loads the authenticated user from the Supabase Auth server.
 *
 * <p>The resource server has already validated the token before this adapter
 * is called. The Auth server lookup is still required for trusted profile
 * data, especially e-mail confirmation, and deliberately ignores
 * {@code user_metadata}.</p>
 */
@Component
public final class SupabaseAuthenticatedIdentityAdapter
        implements AuthenticatedIdentityPort {

    private static final String USER_PATH = "/user";

    private final RestOperations restOperations;
    private final String userEndpoint;
    private final String publishableKey;

    @Autowired
    public SupabaseAuthenticatedIdentityAdapter(
            SupabaseJwtProperties properties,
            RestOperations restOperations) {
        this(
                restOperations,
                userEndpoint(properties.issuer()),
                properties.publishableKey());
    }

    SupabaseAuthenticatedIdentityAdapter(
            RestOperations restOperations,
            String userEndpoint,
            String publishableKey) {
        this.restOperations = restOperations;
        this.userEndpoint = requireText(userEndpoint, "userEndpoint");
        this.publishableKey = requireText(publishableKey, "publishableKey");
    }

    @Override
    public Optional<AuthenticatedIdentityProfile> loadVerifiedProfile(
            AccessTokenContext accessTokenContext) {
        if (accessTokenContext == null
                || !ExternalSubject.SUPABASE_PROVIDER.equals(
                        accessTokenContext.subject().provider())) {
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenContext.accessToken());
        headers.set("apikey", publishableKey);

        try {
            ResponseEntity<SupabaseUserResponse> response = restOperations.exchange(
                    userEndpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    SupabaseUserResponse.class);
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null) {
                return Optional.empty();
            }
            return trustedProfile(response.getBody(), accessTokenContext);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    private static Optional<AuthenticatedIdentityProfile> trustedProfile(
            SupabaseUserResponse response,
            AccessTokenContext accessTokenContext) {
        if (response.id() == null
                || response.id().isBlank()
                || response.email() == null
                || response.email().isBlank()
                || response.emailConfirmedAt() == null
                || response.emailConfirmedAt().isBlank()) {
            return Optional.empty();
        }

        ExternalSubject responseSubject = ExternalSubject.fromSupabase(response.id());
        if (!responseSubject.equals(accessTokenContext.subject())) {
            return Optional.empty();
        }

        try {
            return Optional.of(new AuthenticatedIdentityProfile(
                    responseSubject,
                    NormalizedEmail.from(response.email()),
                    true));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String userEndpoint(String issuer) {
        String normalizedIssuer = requireText(issuer, "issuer");
        return normalizedIssuer.endsWith("/")
                ? normalizedIssuer.substring(0, normalizedIssuer.length() - 1) + USER_PATH
                : normalizedIssuer + USER_PATH;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be configured");
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SupabaseUserResponse(
            String id,
            String email,
            @JsonProperty("email_confirmed_at") String emailConfirmedAt) {
    }
}
