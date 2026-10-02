package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.establishment;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "establishment", schema = "operations")
public class Establishment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Establishment() {
    }

    private Establishment(UUID id, UUID tenantId, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.displayName = normalizeDisplayName(name);
        this.normalizedName = displayName.toLowerCase(Locale.ROOT);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Establishment register(UUID id, UUID tenantId, String name, Instant createdAt) {
        return new Establishment(id, tenantId, name, createdAt);
    }

    private static String normalizeDisplayName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String cleaned = name.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("establishment name must not be blank");
        }
        return cleaned;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String displayName() { return displayName; }
    public String normalizedName() { return normalizedName; }
    public Instant createdAt() { return createdAt; }
}
