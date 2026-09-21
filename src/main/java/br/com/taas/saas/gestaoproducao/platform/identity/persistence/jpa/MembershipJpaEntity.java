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
