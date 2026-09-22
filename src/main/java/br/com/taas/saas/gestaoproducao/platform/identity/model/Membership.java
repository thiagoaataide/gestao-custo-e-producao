package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Membership {

    private final UUID id;
    private final UUID identityId;
    private final UUID tenantId;
    private final MembershipStatus status;
    private final MembershipRole role;
    private final Instant createdAt;
    private final Instant revokedAt;

    public Membership(
            UUID id,
            UUID identityId,
            UUID tenantId,
            MembershipStatus status,
            MembershipRole role,
            Instant createdAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (status == MembershipStatus.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("revoked membership must have revokedAt");
        }
        if (status != MembershipStatus.REVOKED && revokedAt != null) {
            throw new IllegalArgumentException("only revoked membership may have revokedAt");
        }
        this.revokedAt = revokedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID identityId() {
        return identityId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public MembershipStatus status() {
        return status;
    }

    public MembershipRole role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE && revokedAt == null;
    }

    public Membership revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        if (status == MembershipStatus.REVOKED) {
            throw new IllegalStateException("membership is already revoked");
        }
        if (revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        return new Membership(
                id,
                identityId,
                tenantId,
                MembershipStatus.REVOKED,
                role,
                createdAt,
                revokedAt);
    }
}
