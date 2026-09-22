package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.util.Locale;
import java.util.Objects;

public enum PlatformRole {
    PLATFORM_OWNER,
    PLATFORM_ADMIN;

    public static PlatformRole from(String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("platform role must not be blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown platform role: " + value, exception);
        }
    }

    public boolean isOwner() {
        return this == PLATFORM_OWNER;
    }

    public boolean isAdmin() {
        return this == PLATFORM_ADMIN;
    }
}
