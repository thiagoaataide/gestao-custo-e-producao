package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Invitation {

    private final UUID id;
    private final UUID tenantId;
    private final NormalizedEmail email;
    private final MembershipRole role;
    private final InvitationStatus status;
    private final InvitationTokenDigest tokenDigest;
    private final UUID identityId;
    private final Instant expiresAt;
    private final Instant acceptedAt;
    private final Instant revokedAt;
    private final UUID createdBy;
    private final Instant createdAt;

    public Invitation(
            UUID id,
            UUID tenantId,
            NormalizedEmail email,
            MembershipRole role,
            InvitationStatus status,
            InvitationTokenDigest tokenDigest,
            UUID identityId,
            Instant expiresAt,
            Instant acceptedAt,
            Instant revokedAt,
            UUID createdBy,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        if (!MembershipRole.TENANT_USER.equals(role)) {
            throw new IllegalArgumentException("invitation role must be TENANT_USER");
        }
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.tokenDigest = Objects.requireNonNull(tokenDigest, "tokenDigest must not be null");
        this.identityId = identityId;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        validateTransitionTimestamps();
    }

    private void validateTransitionTimestamps() {
        switch (status) {
            case PENDING, EXPIRED -> {
                if (acceptedAt != null || revokedAt != null) {
                    throw new IllegalArgumentException(
                            "pending or expired invitation must not have transition timestamps");
                }
            }
            case ACCEPTED -> {
                if (identityId == null || acceptedAt == null || revokedAt != null) {
                    throw new IllegalArgumentException(
                            "accepted invitation must have identity and acceptedAt only");
                }
            }
            case REVOKED -> {
                if (acceptedAt != null || revokedAt == null) {
                    throw new IllegalArgumentException(
                            "revoked invitation must have revokedAt only");
                }
            }
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public NormalizedEmail email() {
        return email;
    }

    public MembershipRole role() {
        return role;
    }

    public InvitationStatus status() {
        return status;
    }

    public InvitationTokenDigest tokenDigest() {
        return tokenDigest;
    }

    public UUID identityId() {
        return identityId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isPendingAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    public Invitation accept(UUID acceptedIdentityId, Instant acceptedAt) {
        Objects.requireNonNull(acceptedIdentityId, "acceptedIdentityId must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        if (!isPendingAt(acceptedAt)) {
            throw new IllegalStateException("only a valid pending invitation can be accepted");
        }
        return new Invitation(
                id,
                tenantId,
                email,
                role,
                InvitationStatus.ACCEPTED,
                tokenDigest,
                acceptedIdentityId,
                expiresAt,
                acceptedAt,
                null,
                createdBy,
                createdAt);
    }

    public Invitation revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException("only a pending invitation can be revoked");
        }
        return new Invitation(
                id,
                tenantId,
                email,
                role,
                InvitationStatus.REVOKED,
                tokenDigest,
                identityId,
                expiresAt,
                null,
                revokedAt,
                createdBy,
                createdAt);
    }

    public Invitation expire() {
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException("only a pending invitation can expire");
        }
        return new Invitation(
                id,
                tenantId,
                email,
                role,
                InvitationStatus.EXPIRED,
                tokenDigest,
                identityId,
                expiresAt,
                null,
                null,
                createdBy,
                createdAt);
    }
}
