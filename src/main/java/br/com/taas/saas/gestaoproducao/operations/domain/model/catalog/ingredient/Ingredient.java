package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingredient", schema = "operations")
public class Ingredient {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "quantity_dimension", nullable = false, length = 16, updatable = false)
    private String quantityDimension;

    @Column(name = "base_unit", nullable = false, length = 8, updatable = false)
    private String baseUnit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Ingredient() {
    }

    private Ingredient(UUID id, UUID tenantId, IngredientName name,
            IngredientBaseUnit baseUnit, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        IngredientName requiredName = Objects.requireNonNull(name, "name must not be null");
        IngredientBaseUnit requiredUnit = Objects.requireNonNull(baseUnit, "baseUnit must not be null");
        this.displayName = requiredName.displayName();
        this.normalizedName = requiredName.normalizedName();
        this.quantityDimension = requiredUnit.dimension().name();
        this.baseUnit = requiredUnit.code();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Ingredient register(
            UUID id, UUID tenantId, String name, IngredientBaseUnit baseUnit, Instant createdAt) {
        return new Ingredient(id, tenantId, new IngredientName(name), baseUnit, createdAt);
    }

    public void rename(String newName) {
        IngredientName renamed = new IngredientName(newName);
        this.displayName = renamed.displayName();
        this.normalizedName = renamed.normalizedName();
    }

    public boolean hasName(IngredientName name) {
        return normalizedName.equals(Objects.requireNonNull(name, "name must not be null").normalizedName());
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String displayName() { return displayName; }
    public String normalizedName() { return normalizedName; }
    public IngredientQuantityDimension quantityDimension() {
        return IngredientQuantityDimension.valueOf(quantityDimension);
    }
    public IngredientBaseUnit baseUnit() { return IngredientBaseUnit.fromCode(baseUnit); }
    public Instant createdAt() { return createdAt; }
}
