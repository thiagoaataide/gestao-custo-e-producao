package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

class SupabasePasswordAuthenticationProviderTests {

    private static final String SUBJECT = "subject-123";
    private static final String ACCESS_TOKEN = "signed-access-token";
    private static final String REFRESH_TOKEN = "refresh-token-1";

    private SupabaseAuthClient authClient;
    private JwtDecoder jwtDecoder;
    private SupabasePasswordAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        authClient = mock(SupabaseAuthClient.class);
        jwtDecoder = mock(JwtDecoder.class);
        provider = new SupabasePasswordAuthenticationProvider(
                authClient,
                jwtDecoder,
                new SupabaseJwtAuthenticationConverter());
    }

    @Test
    void validatesTheReturnedJwtAndKeepsOnlyTheExternalSubjectAsPrincipal() {
        Jwt jwt = jwt(ACCESS_TOKEN, SUBJECT);
        when(authClient.signInWithPassword("user@example.com", "sensitive-password"))
                .thenReturn(session(SUBJECT));
        when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(jwt);

        SupabaseAuthenticationToken authenticated = (SupabaseAuthenticationToken) provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        " user@example.com ", "sensitive-password"));

        assertThat(authenticated.isAuthenticated()).isTrue();
        assertThat(authenticated.getPrincipal())
                .isEqualTo(ExternalSubject.fromSupabase(SUBJECT));
        assertThat(authenticated.getAuthorities()).isEmpty();
        assertThat(authenticated.getJwt()).isSameAs(jwt);
        assertThat(authenticated.hasRefreshToken()).isTrue();
        assertThat(authenticated.refreshToken()).isEqualTo(REFRESH_TOKEN);
        verify(jwtDecoder).decode(ACCESS_TOKEN);
    }

    @Test
    void givesTheSameCredentialFailureForRejectedPassword() {
        when(authClient.signInWithPassword("user@example.com", "wrong-password"))
                .thenThrow(new SupabaseAuthClient.CredentialsRejectedException());

        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "user@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("E-mail ou senha inválidos.");
    }

    @Test
    void rejectsAResponseWhoseUserDoesNotMatchTheValidatedJwt() {
        when(authClient.signInWithPassword("user@example.com", "sensitive-password"))
                .thenReturn(session("different-subject"));
        when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(jwt(ACCESS_TOKEN, SUBJECT));

        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "user@example.com", "sensitive-password")))
                .isInstanceOf(AuthenticationServiceException.class)
                .hasMessage("Não foi possível autenticar no provedor de identidade.");
    }

    @Test
    void failsClosedWhenTheReturnedJwtCannotBeValidated() {
        when(authClient.signInWithPassword("user@example.com", "sensitive-password"))
                .thenReturn(session(SUBJECT));
        when(jwtDecoder.decode(ACCESS_TOKEN)).thenThrow(new JwtException("invalid signature"));

        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "user@example.com", "sensitive-password")))
                .isInstanceOf(AuthenticationServiceException.class)
                .hasMessage("Não foi possível autenticar no provedor de identidade.");
    }

    @Test
    void doesNotReturnProviderDetailsWhenSupabaseIsUnavailable() {
        when(authClient.signInWithPassword("user@example.com", "sensitive-password"))
                .thenThrow(new SupabaseAuthClient.ProviderUnavailableException());

        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "user@example.com", "sensitive-password")))
                .isInstanceOf(AuthenticationServiceException.class)
                .hasMessage("Não foi possível autenticar no provedor de identidade.");
    }

    private static SupabaseAuthClient.SupabaseAuthSession session(String subject) {
        return new SupabaseAuthClient.SupabaseAuthSession(
                ACCESS_TOKEN,
                REFRESH_TOKEN,
                subject);
    }

    private static Jwt jwt(String token, String subject) {
        return Jwt.withTokenValue(token)
                .header("alg", "ES256")
                .subject(subject)
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
