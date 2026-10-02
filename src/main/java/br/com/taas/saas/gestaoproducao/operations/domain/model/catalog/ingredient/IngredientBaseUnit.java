package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient;

import java.util.Arrays;

public enum IngredientBaseUnit {
    GRAM("g", IngredientQuantityDimension.MASS),
    MILLILITER("ml", IngredientQuantityDimension.VOLUME),
    UNIT("un", IngredientQuantityDimension.COUNT);

    private final String code;
    private final IngredientQuantityDimension dimension;

    IngredientBaseUnit(String code, IngredientQuantityDimension dimension) {
        this.code = code;
        this.dimension = dimension;
    }

    public String code() { return code; }
    public IngredientQuantityDimension dimension() { return dimension; }

    public static IngredientBaseUnit fromCode(String code) {
        if (code == null) throw new IllegalArgumentException("base unit must not be null");
        return Arrays.stream(values()).filter(unit -> unit.code.equalsIgnoreCase(code.trim())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported base unit: " + code));
    }
}
