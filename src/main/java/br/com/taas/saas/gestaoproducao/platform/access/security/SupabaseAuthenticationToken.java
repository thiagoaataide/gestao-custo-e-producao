package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.util.Collections;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

/**
 * Authenticated token whose principal is the provider-neutral external
 * subject. Domain authorization is deliberately not represented by JWT
 * claims or provider authorities.
 */
public final class SupabaseAuthenticationToken extends AbstractAuthenticationToken {

    private final Jwt jwt;
    private final ExternalSubject externalSubject;
    private final String refreshToken;

    public SupabaseAuthenticationToken(Jwt jwt, ExternalSubject externalSubject) {
        this(jwt, externalSubject, null);
    }

    public SupabaseAuthenticationToken(
            Jwt jwt,
            ExternalSubject externalSubject,
            String refreshToken) {
        super(Collections.emptyList());
        this.jwt = jwt;
        this.externalSubject = externalSubject;
        this.refreshToken = refreshToken;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt.getTokenValue();
    }

    @Override
    public Object getPrincipal() {
        return externalSubject;
    }

    @Override
    public String getName() {
        return externalSubject.value();
    }

    public Jwt getJwt() {
        return jwt;
    }

    boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    String refreshToken() {
        return refreshToken;
    }
}
