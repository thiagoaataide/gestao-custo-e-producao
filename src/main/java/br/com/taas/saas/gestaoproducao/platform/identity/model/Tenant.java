package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Tenant {

    private static final String DEFAULT_NAME = "Tenant";

    private final UUID id;
    private final String name;
    private final TenantStatus status;
    private final Instant createdAt;

    public Tenant(UUID id, TenantStatus status, Instant createdAt) {
        this(id, DEFAULT_NAME, status, createdAt);
    }

    public Tenant(UUID id, String name, TenantStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = requireName(name);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TenantStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isAvailable() {
        return status == TenantStatus.ACTIVE;
    }

    public Tenant suspend() {
        if (status != TenantStatus.ACTIVE) {
            throw new IllegalStateException("only an active tenant can be suspended");
        }
        return withStatus(TenantStatus.SUSPENDED);
    }

    public Tenant reactivate() {
        if (status != TenantStatus.SUSPENDED) {
            throw new IllegalStateException("only a suspended tenant can be reactivated");
        }
        return withStatus(TenantStatus.ACTIVE);
    }

    public Tenant close() {
        if (status == TenantStatus.CLOSED) {
            throw new IllegalStateException("closed tenant is terminal");
        }
        return withStatus(TenantStatus.CLOSED);
    }

    private Tenant withStatus(TenantStatus nextStatus) {
        return new Tenant(id, name, nextStatus, createdAt);
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String normalized = name.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return normalized;
    }
}
