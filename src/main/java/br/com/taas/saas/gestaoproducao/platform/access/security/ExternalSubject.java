package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.util.Objects;

/**
 * Minimal external identity key used to bridge provider authentication and
 * domain-owned authorization.
 */
public record ExternalSubject(String provider, String value) {

    public static final String SUPABASE_PROVIDER = "SUPABASE";

    public ExternalSubject {
        provider = requireText(provider, "provider");
        value = requireText(value, "value");
    }

    public static ExternalSubject fromSupabase(String subject) {
        return new ExternalSubject(SUPABASE_PROVIDER, subject);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
