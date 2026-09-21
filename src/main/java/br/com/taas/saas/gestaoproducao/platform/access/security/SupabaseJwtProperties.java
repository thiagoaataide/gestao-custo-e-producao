package br.com.taas.saas.gestaoproducao.platform.access.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase.jwt")
public record SupabaseJwtProperties(
        String issuer,
        String jwkSetUri,
        String publishableKey,
        String audience) {

    public SupabaseJwtProperties {
        issuer = required(issuer, "issuer");
        jwkSetUri = required(jwkSetUri, "jwkSetUri");
        publishableKey = required(publishableKey, "publishableKey");
        audience = audience == null || audience.isBlank() ? null : audience;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("supabase.jwt." + fieldName + " must be configured");
        }
        return value;
    }
}
