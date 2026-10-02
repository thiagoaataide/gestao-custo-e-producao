package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient;

import java.math.BigDecimal;
import java.util.Objects;

public record IngredientQuantity(BigDecimal amount, IngredientUnit unit) {

    public IngredientQuantity {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        if (amount.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (unit.baseUnit() == IngredientBaseUnit.UNIT) {
            try {
                amount.toBigIntegerExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("unit quantity must be a whole number", exception);
            }
        }
    }

    public static IngredientQuantity of(String amount, String unit) {
        Objects.requireNonNull(amount, "amount must not be null");
        return new IngredientQuantity(new BigDecimal(amount), IngredientUnit.fromCode(unit));
    }

    public IngredientQuantity toBase() {
        return new IngredientQuantity(amount.multiply(unit.factorToBase()),
                IngredientUnit.fromCode(unit.baseUnit().code()));
    }

    public IngredientQuantity add(IngredientQuantity other) {
        IngredientQuantity left = toBase();
        IngredientQuantity right = Objects.requireNonNull(other, "other quantity must not be null").toBase();
        if (left.unit.baseUnit() != right.unit.baseUnit()) {
            throw new IllegalArgumentException("quantities from different dimensions cannot be combined");
        }
        return new IngredientQuantity(left.amount.add(right.amount), left.unit);
    }
}
