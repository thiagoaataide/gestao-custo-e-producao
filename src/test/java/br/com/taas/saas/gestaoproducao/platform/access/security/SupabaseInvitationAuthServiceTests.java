package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.application.port.out.AuthenticatedIdentityPort;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

class SupabaseInvitationAuthServiceTests {

    private static final String SUBJECT = "invitee-subject";
    private static final String TOKEN = "verified-access-token";
    private SupabaseAuthClient authClient;
    private JwtDecoder jwtDecoder;
    private AuthenticatedIdentityPort identityPort;
    private SupabaseInvitationAuthService service;

    @BeforeEach
    void setUp() {
        authClient = mock(SupabaseAuthClient.class);
        jwtDecoder = mock(JwtDecoder.class);
        identityPort = mock(AuthenticatedIdentityPort.class);
        service = new SupabaseInvitationAuthService(
                authClient, jwtDecoder, new SupabaseJwtAuthenticationConverter(), identityPort);
    }

    @Test
    void otpSessionRequiresVerifiedSupabaseProfileForTheInvitedEmail() {
        when(authClient.verifyEmailOtp("invitee@outlook.com", "123456"))
                .thenReturn(new SupabaseAuthClient.SupabaseAuthSession(TOKEN, "refresh", SUBJECT));
        Jwt jwt = jwt();
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);
        when(identityPort.loadVerifiedProfile(new AccessTokenContext(
                ExternalSubject.fromSupabase(SUBJECT), TOKEN)))
                .thenReturn(Optional.of(new AuthenticatedIdentityProfile(
                        ExternalSubject.fromSupabase(SUBJECT),
                        NormalizedEmail.from("Invitee@Outlook.com"), true)));

        SupabaseAuthenticationToken result = service.verifySignupOtp("invitee@outlook.com", "123456");

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isEqualTo(ExternalSubject.fromSupabase(SUBJECT));
        assertThat(result.getJwt()).isSameAs(jwt);
        verify(identityPort).loadVerifiedProfile(new AccessTokenContext(
                ExternalSubject.fromSupabase(SUBJECT), TOKEN));
    }

    @Test
    void otpSessionForAnotherEmailIsRejectedBeforeItCanBecomeApplicationAuthentication() {
        when(authClient.verifyEmailOtp("invitee@outlook.com", "123456"))
                .thenReturn(new SupabaseAuthClient.SupabaseAuthSession(TOKEN, "refresh", SUBJECT));
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwt());
        when(identityPort.loadVerifiedProfile(new AccessTokenContext(
                ExternalSubject.fromSupabase(SUBJECT), TOKEN)))
                .thenReturn(Optional.of(new AuthenticatedIdentityProfile(
                        ExternalSubject.fromSupabase(SUBJECT),
                        NormalizedEmail.from("owner@gmail.com"), true)));

        assertThatThrownBy(() -> service.verifySignupOtp("invitee@outlook.com", "123456"))
                .isInstanceOf(SupabaseAuthClient.InvalidAuthResponseException.class);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "ES256")
                .subject(SUBJECT)
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
