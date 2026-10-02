package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

public class IngredientNameAlreadyRegisteredException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public IngredientNameAlreadyRegisteredException(String name) {
        super("An ingredient with normalized name '" + name + "' is already registered in this tenant");
    }
}
