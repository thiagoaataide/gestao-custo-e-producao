package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@Entity
@Table(name = "tenant", schema = "platform")
public class TenantJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TenantJpaEntity() {
    }

    private TenantJpaEntity(
            UUID id,
            String name,
            TenantStatus status,
            Instant createdAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static TenantJpaEntity fromDomain(Tenant tenant) {
        return new TenantJpaEntity(
                tenant.id(),
                tenant.name(),
                tenant.status(),
                tenant.createdAt());
    }

    public Tenant toDomain() {
        return new Tenant(id, name, status, createdAt);
    }
}
