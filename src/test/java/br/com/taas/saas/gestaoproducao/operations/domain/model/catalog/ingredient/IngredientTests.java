package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IngredientTests {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    void registerTrimsDisplayNameAndNormalizesCaseAccentsAndWhitespace() {
        Ingredient ingredient = ingredient("  Açúcar   CRISTAL ", IngredientBaseUnit.GRAM);

        assertThat(ingredient.displayName()).isEqualTo("Açúcar CRISTAL");
        assertThat(ingredient.normalizedName()).isEqualTo("acucar cristal");
        assertThat(ingredient.quantityDimension()).isEqualTo(IngredientQuantityDimension.MASS);
        assertThat(ingredient.baseUnit()).isEqualTo(IngredientBaseUnit.GRAM);
    }

    @Test
    void rejectsBlankIngredientName() {
        assertThatThrownBy(() -> ingredient("   ", IngredientBaseUnit.GRAM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void genericSugarNameRequiresTheVariantToBeSpecified() {
        assertThatThrownBy(() -> ingredient("açúcar", IngredientBaseUnit.GRAM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("specify the sugar type");
    }

    @Test
    void specificSugarVariantsRemainDistinctAndRenameKeepsIdentityAndUnit() {
        Ingredient crystal = ingredient("Açúcar cristal", IngredientBaseUnit.GRAM);
        Ingredient brown = ingredient("Açúcar mascavo", IngredientBaseUnit.GRAM);
        UUID originalId = crystal.id();

        assertThat(crystal.id()).isNotEqualTo(brown.id());
        assertThat(crystal.hasName(new IngredientName("acucar CRISTAL"))).isTrue();
        assertThat(crystal.hasName(new IngredientName("açúcar mascavo"))).isFalse();

        crystal.rename("  Açúcar   Demerara ");
        assertThat(crystal.displayName()).isEqualTo("Açúcar Demerara");
        assertThat(crystal.normalizedName()).isEqualTo("acucar demerara");
        assertThat(crystal.id()).isEqualTo(originalId);
        assertThat(crystal.tenantId()).isEqualTo(TENANT_ID);
        assertThat(crystal.baseUnit()).isEqualTo(IngredientBaseUnit.GRAM);
        assertThat(brown.displayName()).isEqualTo("Açúcar mascavo");
    }

    @Test
    void convertsAndCombinesWeightExactlyInGrams() {
        IngredientQuantity total = IngredientQuantity.of("2", "kg")
                .add(IngredientQuantity.of("278", "g"));

        assertThat(total.amount()).isEqualByComparingTo("2278");
        assertThat(total.unit()).isEqualTo(IngredientUnit.GRAM);
        assertThat(IngredientQuantity.of("1", "kg").toBase().amount())
                .isEqualByComparingTo("1000");
    }

    @Test
    void convertsVolumeAndCountToTheirBaseUnitsAndRejectsInvalidMixes() {
        assertThat(IngredientQuantity.of("1", "l").toBase().amount()).isEqualByComparingTo("1000");
        assertThat(IngredientQuantity.of("1", "l").toBase().unit()).isEqualTo(IngredientUnit.MILLILITER);
        assertThat(IngredientQuantity.of("3", "un").toBase().amount()).isEqualByComparingTo("3");
        assertThatThrownBy(() -> IngredientQuantity.of("1", "kg").add(IngredientQuantity.of("1", "ml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different dimensions");
        assertThatThrownBy(() -> IngredientQuantity.of("1.5", "un"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole number");
    }

    private Ingredient ingredient(String name, IngredientBaseUnit baseUnit) {
        return Ingredient.register(UUID.randomUUID(), TENANT_ID, name, baseUnit, CREATED_AT);
    }
}
