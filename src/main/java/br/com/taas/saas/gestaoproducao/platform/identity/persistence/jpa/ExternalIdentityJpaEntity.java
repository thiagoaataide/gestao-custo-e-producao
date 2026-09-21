package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentityStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@Entity
@Table(
        name = "external_identity",
        schema = "platform",
        uniqueConstraints = @UniqueConstraint(
                name = "external_identity_provider_subject_uq",
                columnNames = {"provider", "external_subject"}))
public class ExternalIdentityJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "external_subject", nullable = false, length = 255)
    private String externalSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExternalIdentityStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExternalIdentityJpaEntity() {
    }

    public ExternalIdentity toDomain() {
        return new ExternalIdentity(
                id,
                new ExternalSubject(provider, externalSubject),
                status,
                createdAt);
    }
}
