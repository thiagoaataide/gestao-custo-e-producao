package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.util.Locale;
import java.util.Objects;

public record NormalizedEmail(String value) {

    public NormalizedEmail {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    public static NormalizedEmail from(String value) {
        return new NormalizedEmail(value);
    }
}
