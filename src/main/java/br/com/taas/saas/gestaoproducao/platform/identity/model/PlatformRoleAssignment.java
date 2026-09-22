package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PlatformRoleAssignment {

    private final UUID id;
    private final UUID identityId;
    private final PlatformRole role;
    private final PlatformRoleStatus status;
    private final Instant createdAt;
    private final Instant revokedAt;

    public PlatformRoleAssignment(
            UUID id,
            UUID identityId,
            PlatformRole role,
            PlatformRoleStatus status,
            Instant createdAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (status == PlatformRoleStatus.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("revoked platform role must have revokedAt");
        }
        if (status == PlatformRoleStatus.ACTIVE && revokedAt != null) {
            throw new IllegalArgumentException("active platform role must not have revokedAt");
        }
        this.revokedAt = revokedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID identityId() {
        return identityId;
    }

    public PlatformRole role() {
        return role;
    }

    public PlatformRoleStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return status == PlatformRoleStatus.ACTIVE;
    }

    public PlatformRoleAssignment revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        if (!isActive()) {
            throw new IllegalStateException("only an active platform role can be revoked");
        }
        return new PlatformRoleAssignment(
                id,
                identityId,
                role,
                PlatformRoleStatus.REVOKED,
                createdAt,
                revokedAt);
    }
}
