package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.taas.saas.gestaoproducao.platform.identity.model.In­vitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

@Entity
@Table(name = "invitation", schema = "platform")
public class InvitationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 32)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status;

    @Column(name = "token_digest", nullable = false)
    private String tokenDigest;

    @Column(name = "identity_id")
    private UUID identityId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvitationJpaEntity() {
    }

    private InvitationJpaEntity(
            UUID id,
            UUID tenantId,
            String email,
            String role,
            InvitationStatus status,
            String tokenDigest,
            UUID identityId,
            Instant expiresAt,
            Instant acceptedAt,
            Instant revokedAt,
            UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.role = role;
        this.status = status;
        this.tokenDigest = tokenDigest;
        this.identityId = identityId;
        this.expiresAt = expiresAt;
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static InvitationJpaEntity fromDomain(Invitation invitation) {
        return new InvitationJpaEntity(
                invitation.id(),
                invitation.tenantId(),
                invitation.email().value(),
                invitation.role().value(),
                invitation.status(),
                invitation.tokenDigest().value(),
                invitation.identityId(),
                invitation.expiresAt(),
                invitation.acceptedAt(),
                invitation.revokedAt(),
                invitation.createdBy(),
                invitation.createdAt());
    }

    public Invitation toDomain() {
        return new Invitation(
                id,
                tenantId,
                NormalizedEmail.from(email),
                new MembershipRole(role),
                status,
                new InvitationTokenDigest(tokenDigest),
                identityId,
                expiresAt,
                acceptedAt,
                revokedAt,
                createdBy,
                createdAt);
    }
}
