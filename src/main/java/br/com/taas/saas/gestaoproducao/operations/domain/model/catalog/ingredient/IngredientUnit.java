package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient;

import java.math.BigDecimal;
import java.util.Arrays;

public enum IngredientUnit {
    GRAM("g", IngredientBaseUnit.GRAM, "1"),
    KILOGRAM("kg", IngredientBaseUnit.GRAM, "1000"),
    MILLILITER("ml", IngredientBaseUnit.MILLILITER, "1"),
    LITER("l", IngredientBaseUnit.MILLILITER, "1000"),
    UNIT("un", IngredientBaseUnit.UNIT, "1");

    private final String code;
    private final IngredientBaseUnit baseUnit;
    private final BigDecimal factorToBase;

    IngredientUnit(String code, IngredientBaseUnit baseUnit, String factorToBase) {
        this.code = code;
        this.baseUnit = baseUnit;
        this.factorToBase = new BigDecimal(factorToBase);
    }

    public String code() { return code; }
    public IngredientBaseUnit baseUnit() { return baseUnit; }
    BigDecimal factorToBase() { return factorToBase; }

    public static IngredientUnit fromCode(String code) {
        if (code == null) throw new IllegalArgumentException("unit must not be null");
        return Arrays.stream(values()).filter(unit -> unit.code.equalsIgnoreCase(code.trim())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported unit: " + code));
    }
}
