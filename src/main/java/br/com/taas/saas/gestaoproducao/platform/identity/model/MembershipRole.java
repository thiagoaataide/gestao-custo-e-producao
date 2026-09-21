package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.util.Objects;

public record MembershipRole(String value) {

    private static final int MAX_LENGTH = 32;
    public static final MembershipRole TENANT_USER = new MembershipRole("TENANT_USER");
    public static final MembershipRole PLATFORM_ADMIN = new MembershipRole("PLATFORM_ADMIN");

    public MembershipRole {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("value must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public boolean isPlatformAdmin() {
        return PLATFORM_ADMIN.value.equals(value);
    }
}
