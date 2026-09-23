package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.util.Objects;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

/** Delegates existing-user password authentication to Supabase Auth. */
@Component
public final class SupabasePasswordAuthenticationProvider implements AuthenticationProvider {

    private final SupabaseAuthClient authClient;
    private final JwtDecoder jwtDecoder;
    private final SupabaseJwtAuthenticationConverter authenticationConverter;

    public SupabasePasswordAuthenticationProvider(
            SupabaseAuthClient authClient,
            JwtDecoder jwtDecoder,
            SupabaseJwtAuthenticationConverter authenticationConverter) {
        this.authClient = Objects.requireNonNull(authClient);
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder);
        this.authenticationConverter = Objects.requireNonNull(authenticationConverter);
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return null;
        }

        String email = authentication.getName();
        Object suppliedPassword = authentication.getCredentials();
        if (email == null || email.isBlank()
                || !(suppliedPassword instanceof String password)
                || password.isBlank()) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }

        try {
            SupabaseAuthClient.SupabaseAuthSession session =
                    authClient.signInWithPassword(email.trim(), password);
            Jwt jwt = jwtDecoder.decode(session.accessToken());
            SupabaseAuthenticationToken converted = authenticationConverter.convert(jwt);
            ExternalSubject jwtSubject = (ExternalSubject) converted.getPrincipal();
            ExternalSubject responseSubject = ExternalSubject.fromSupabase(session.subject());
            if (!jwtSubject.equals(responseSubject)) {
                throw new SupabaseAuthClient.InvalidAuthResponseException();
            }
            return new SupabaseAuthenticationToken(
                    jwt,
                    jwtSubject,
                    session.refreshToken());
        } catch (SupabaseAuthClient.CredentialsRejectedException exception) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        } catch (SupabaseAuthClient.ProviderUnavailableException
                | SupabaseAuthClient.InvalidAuthResponseException
                | JwtException
                | OAuth2AuthenticationException
                | IllegalArgumentException exception) {
            throw new AuthenticationServiceException(
                    "Não foi possível autenticar no provedor de identidade.");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
