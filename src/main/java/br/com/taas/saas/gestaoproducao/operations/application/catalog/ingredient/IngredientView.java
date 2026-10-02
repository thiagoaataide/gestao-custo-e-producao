package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import java.util.Objects;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.Ingredient;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientQuantityDimension;

public record IngredientView(UUID id, String name,
        IngredientQuantityDimension quantityDimension, String baseUnit) {

    public IngredientView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(quantityDimension, "quantityDimension must not be null");
        Objects.requireNonNull(baseUnit, "baseUnit must not be null");
    }

    public static IngredientView from(Ingredient ingredient) {
        return new IngredientView(ingredient.id(), ingredient.displayName(),
                ingredient.quantityDimension(), ingredient.baseUnit().code());
    }
}
