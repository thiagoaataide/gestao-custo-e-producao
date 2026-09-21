package br.com.taas.saas.gestaoproducao.platform.access.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

/**
 * Converts a cryptographically validated Supabase JWT into the minimum
 * external identity required by the domain boundary.
 */
@Component
public final class SupabaseJwtAuthenticationConverter
        implements Converter<Jwt, SupabaseAuthenticationToken> {

    @Override
    public SupabaseAuthenticationToken convert(Jwt jwt) {
        Object subjectClaim = jwt.getClaims().get("sub");
        if (!(subjectClaim instanceof String subject) || subject.isBlank()) {
            throw invalidToken("JWT claim 'sub' must be a non-blank string");
        }

        return new SupabaseAuthenticationToken(jwt, ExternalSubject.fromSupabase(subject));
    }

    private static OAuth2AuthenticationException invalidToken(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), message);
    }
}
