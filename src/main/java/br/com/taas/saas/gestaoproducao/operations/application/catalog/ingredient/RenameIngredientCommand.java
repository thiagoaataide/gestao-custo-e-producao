package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import java.util.Objects;
import java.util.UUID;

public record RenameIngredientCommand(UUID ingredientId, String newName) {

    public RenameIngredientCommand {
        Objects.requireNonNull(ingredientId, "ingredientId must not be null");
        Objects.requireNonNull(newName, "newName must not be null");
    }
}
