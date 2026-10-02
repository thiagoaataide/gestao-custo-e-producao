package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import java.util.UUID;

public class IngredientNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public IngredientNotFoundException(UUID ingredientId) {
        super("Ingredient was not found in the authenticated tenant: " + ingredientId);
    }
}
