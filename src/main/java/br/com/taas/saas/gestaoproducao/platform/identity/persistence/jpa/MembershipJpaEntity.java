package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;

@Entity
@Table(name = "membership", schema = "platform")
public class MembershipJpaEntity {

    @Id
    private UUID id;

    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipStatus status;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MembershipJpaEntity() {
    }

    private MembershipJpaEntity(
            UUID id,
            UUID identityId,
            UUID tenantId,
            MembershipStatus status,
            String role,
            Instant createdAt,
            Instant revokedAt) {
        this.id = id;
        this.identityId = identityId;
        this.tenantId = tenantId;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    public static MembershipJpaEntity fromDomain(Membership membership) {
        return new MembershipJpaEntity(
                membership.id(),
                membership.identityId(),
                membership.tenantId(),
                membership.status(),
                membership.role().value(),
                membership.createdAt(),
                membership.revokedAt());
    }

    public Membership toDomain() {
        return new Membership(
                id,
                identityId,
                tenantId,
                status,
                new MembershipRole(role),
                createdAt,
                revokedAt);
    }
}
