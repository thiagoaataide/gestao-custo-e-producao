package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.util.Objects;
import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.application.port.out.AuthenticatedIdentityPort;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

/** Invitation-scoped Supabase Auth operations; this service has no domain identity or membership writer. */
@Service
public class SupabaseInvitationAuthService {

    private final SupabaseAuthClient authClient;
    private final JwtDecoder jwtDecoder;
    private final SupabaseJwtAuthenticationConverter authenticationConverter;
    private final AuthenticatedIdentityPort identityPort;

    public SupabaseInvitationAuthService(
            SupabaseAuthClient authClient,
            JwtDecoder jwtDecoder,
            SupabaseJwtAuthenticationConverter authenticationConverter,
            AuthenticatedIdentityPort identityPort) {
        this.authClient = Objects.requireNonNull(authClient);
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder);
        this.authenticationConverter = Objects.requireNonNull(authenticationConverter);
        this.identityPort = Objects.requireNonNull(identityPort);
    }

    public void signUp(String invitedEmail, String password) {
        authClient.signUpWithPassword(invitedEmail, password);
    }

    public void resendSignupOtp(String invitedEmail) {
        authClient.resendSignupEmailOtp(invitedEmail);
    }

    public void signOut(String accessToken) {
        authClient.signOutLocal(accessToken);
    }

    public SupabaseAuthenticationToken verifySignupOtp(String invitedEmail, String otp) {
        SupabaseAuthClient.SupabaseAuthSession session =
                authClient.verifyEmailOtp(invitedEmail, otp);
        try {
            Jwt jwt = jwtDecoder.decode(session.accessToken());
            SupabaseAuthenticationToken converted = authenticationConverter.convert(jwt);
            ExternalSubject tokenSubject = (ExternalSubject) converted.getPrincipal();
            ExternalSubject responseSubject = ExternalSubject.fromSupabase(session.subject());
            if (!tokenSubject.equals(responseSubject)) {
                throw new SupabaseAuthClient.InvalidAuthResponseException();
            }
            AuthenticatedIdentityProfile profile = verifiedProfile(
                    new AccessTokenContext(tokenSubject, session.accessToken()), invitedEmail)
                    .orElseThrow(SupabaseAuthClient.InvalidAuthResponseException::new);
            if (!profile.emailVerified()) {
                throw new SupabaseAuthClient.InvalidAuthResponseException();
            }
            return new SupabaseAuthenticationToken(jwt, tokenSubject, session.refreshToken());
        } catch (JwtException | OAuth2AuthenticationException | IllegalArgumentException exception) {
            throw new SupabaseAuthClient.InvalidAuthResponseException();
        }
    }

    public Optional<AuthenticatedIdentityProfile> verifiedProfile(
            SupabaseAuthenticationToken authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return identityPort.loadVerifiedProfile(new AccessTokenContext(
                (ExternalSubject) authentication.getPrincipal(),
                authentication.getJwt().getTokenValue()));
    }

    private Optional<AuthenticatedIdentityProfile> verifiedProfile(
            AccessTokenContext context,
            String expectedEmail) {
        NormalizedEmail expected = NormalizedEmail.from(expectedEmail);
        return identityPort.loadVerifiedProfile(context)
                .filter(AuthenticatedIdentityProfile::emailVerified)
                .filter(profile -> expected.equals(profile.email()))
                .filter(profile -> context.subject().equals(profile.subject()));
    }
}
