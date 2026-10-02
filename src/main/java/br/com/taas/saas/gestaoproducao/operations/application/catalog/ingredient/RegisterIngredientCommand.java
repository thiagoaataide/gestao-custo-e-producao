package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import java.util.Objects;

import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientBaseUnit;

public record RegisterIngredientCommand(String name, IngredientBaseUnit baseUnit) {

    public RegisterIngredientCommand {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(baseUnit, "baseUnit must not be null");
    }
}
