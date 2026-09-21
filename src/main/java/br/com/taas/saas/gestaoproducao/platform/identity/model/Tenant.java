package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Tenant {

    private final UUID id;
    private final TenantStatus status;
    private final Instant createdAt;

    public Tenant(UUID id, TenantStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID id() {
        return id;
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
}
