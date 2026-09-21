package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.util.Collections;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Authenticated token whose principal is the provider-neutral external
 * subject. Domain authorization is deliberately not represented by JWT
 * claims or provider authorities.
 */
public final class SupabaseAuthenticationToken extends AbstractAuthenticationToken {

    private final Jwt jwt;
    private final ExternalSubject externalSubject;

    public SupabaseAuthenticationToken(Jwt jwt, ExternalSubject externalSubject) {
        super(Collections.emptyList());
        this.jwt = jwt;
        this.externalSubject = externalSubject;
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
}
