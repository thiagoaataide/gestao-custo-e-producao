package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

@Entity
@Table(name = "platform_role_assignment", schema = "platform")
public class PlatformRoleJpaEntity {

    @Id
    private UUID id;

    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlatformRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlatformRoleStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PlatformRoleJpaEntity() {
    }

    private PlatformRoleJpaEntity(
            UUID id,
            UUID identityId,
            PlatformRole role,
            PlatformRoleStatus status,
            Instant createdAt,
            Instant revokedAt) {
        this.id = id;
        this.identityId = identityId;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    public static PlatformRoleJpaEntity fromDomain(PlatformRoleAssignment assignment) {
        return new PlatformRoleJpaEntity(
                assignment.id(),
                assignment.identityId(),
                assignment.role(),
                assignment.status(),
                assignment.createdAt(),
                assignment.revokedAt());
    }

    public PlatformRoleAssignment toDomain() {
        return new PlatformRoleAssignment(
                id,
                identityId,
                role,
                status,
                createdAt,
                revokedAt);
    }
}
