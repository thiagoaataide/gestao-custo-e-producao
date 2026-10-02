package br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operational_manager_assignment", schema = "platform")
public class OperationalManagerAssignment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OperationalManagerAssignmentStatus status;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected OperationalManagerAssignment() {
    }

    private OperationalManagerAssignment(
            UUID id,
            UUID tenantId,
            UUID membershipId,
            OperationalManagerAssignmentStatus status,
            UUID assignedBy,
            Instant assignedAt,
            UUID revokedBy,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.membershipId = Objects.requireNonNull(membershipId, "membershipId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.assignedBy = Objects.requireNonNull(assignedBy, "assignedBy must not be null");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        if (status == OperationalManagerAssignmentStatus.REVOKED
                && (revokedBy == null || revokedAt == null)) {
            throw new IllegalArgumentException("revoked assignment requires actor and time");
        }
        if (status == OperationalManagerAssignmentStatus.ACTIVE
                && (revokedBy != null || revokedAt != null)) {
            throw new IllegalArgumentException("active assignment cannot have revocation data");
        }
        this.revokedBy = revokedBy;
        this.revokedAt = revokedAt;
    }

    public static OperationalManagerAssignment assign(
            UUID id,
            UUID tenantId,
            UUID membershipId,
            UUID assignedBy,
            Instant assignedAt) {
        return new OperationalManagerAssignment(
                id,
                tenantId,
                membershipId,
                OperationalManagerAssignmentStatus.ACTIVE,
                assignedBy,
                assignedAt,
                null,
                null);
    }

    public void revoke(UUID revokedBy, Instant revokedAt) {
        Objects.requireNonNull(revokedBy, "revokedBy must not be null");
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        if (!isActive()) {
            throw new IllegalStateException("only an active assignment can be revoked");
        }
        if (revokedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("revokedAt must not be before assignedAt");
        }
        this.status = OperationalManagerAssignmentStatus.REVOKED;
        this.revokedBy = revokedBy;
        this.revokedAt = revokedAt;
    }

    public boolean isActive() {
        return status == OperationalManagerAssignmentStatus.ACTIVE;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID membershipId() { return membershipId; }
    public OperationalManagerAssignmentStatus status() { return status; }
    public UUID assignedBy() { return assignedBy; }
    public Instant assignedAt() { return assignedAt; }
    public UUID revokedBy() { return revokedBy; }
    public Instant revokedAt() { return revokedAt; }
}
