package br.com.taas.saas.gestaoproducao.operations.infrastructure.persistence.catalog.ingredient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.IngredientRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.Ingredient;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientName;

@Repository
public class JpaIngredientRepository implements IngredientRepository {

    private final IngredientJpaRepository repository;

    public JpaIngredientRepository(IngredientJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Ingredient> findByTenantIdAndName(UUID tenantId, IngredientName name) {
        return repository.findByTenantIdAndNormalizedName(
                Objects.requireNonNull(tenantId, "tenantId must not be null"),
                Objects.requireNonNull(name, "name must not be null").normalizedName());
    }

    @Override
    public List<Ingredient> findSimilarByTenantId(UUID tenantId, IngredientName name, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return repository.findSimilarByTenantId(
                Objects.requireNonNull(tenantId, "tenantId must not be null"),
                Objects.requireNonNull(name, "name must not be null").normalizedName(),
                PageRequest.of(0, limit));
    }

    @Override
    public Ingredient save(Ingredient ingredient) {
        return repository.saveAndFlush(Objects.requireNonNull(ingredient, "ingredient must not be null"));
    }
}
