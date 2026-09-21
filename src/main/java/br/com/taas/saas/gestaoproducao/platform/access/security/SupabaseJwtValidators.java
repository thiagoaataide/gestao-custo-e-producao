package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

final class SupabaseJwtValidators {

    private SupabaseJwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> create(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> issuerAndTimeValidator = JwtValidators.createDefaultWithIssuer(issuer);
        if (audience == null || audience.isBlank()) {
            return issuerAndTimeValidator;
        }

        JwtClaimValidator<List<String>> audienceValidator = new JwtClaimValidator<>(
                "aud",
                values -> values != null && values.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(issuerAndTimeValidator, audienceValidator);
    }
}
