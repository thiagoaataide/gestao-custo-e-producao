package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import java.util.List;
import java.util.Objects;

public record IngredientCommandResult(IngredientView ingredient, List<IngredientView> similarCandidates) {

    public IngredientCommandResult {
        Objects.requireNonNull(ingredient, "ingredient must not be null");
        similarCandidates = List.copyOf(similarCandidates);
    }
}
