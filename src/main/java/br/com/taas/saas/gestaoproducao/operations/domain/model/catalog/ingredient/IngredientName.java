package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

public record IngredientName(String displayName, String normalizedName) {

    public IngredientName(String value) {
        this(normalizeDisplayName(value), normalizeKey(value));
    }

    public IngredientName {
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(normalizedName, "normalizedName must not be null");
        if (displayName.isBlank() || normalizedName.isBlank()) {
            throw new IllegalArgumentException("ingredient name must not be blank");
        }
        if (!normalizeKey(displayName).equals(normalizedName)) {
            throw new IllegalArgumentException("normalized name must match the display name");
        }
        if (normalizedName.equals("acucar")) {
            throw new IllegalArgumentException("ingredient name must specify the sugar type");
        }
    }

    private static String normalizeDisplayName(String value) {
        Objects.requireNonNull(value, "ingredient name must not be null");
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeKey(String value) {
        String displayName = normalizeDisplayName(value);
        String decomposed = Normalizer.normalize(displayName, Normalizer.Form.NFKD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
