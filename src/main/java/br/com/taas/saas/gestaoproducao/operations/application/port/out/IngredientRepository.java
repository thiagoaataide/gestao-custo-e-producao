package br.com.taas.saas.gestaoproducao.operations.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.Ingredient;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientName;

public interface IngredientRepository {

    Optional<Ingredient> findByTenantIdAndName(UUID tenantId, IngredientName name);

    Optional<Ingredient> findByTenantIdAndId(UUID tenantId, UUID ingredientId);

    List<Ingredient> findSimilarByTenantId(UUID tenantId, IngredientName name, int limit);

    Ingredient save(Ingredient ingredient);
}
