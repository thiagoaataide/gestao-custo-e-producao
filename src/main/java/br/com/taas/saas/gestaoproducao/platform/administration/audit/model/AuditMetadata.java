package br.com.taas.saas.gestaoproducao.platform.administration.audit.model;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AuditMetadata {

    private static final Set<String> FORBIDDEN_KEY_MARKERS = Set.of(
            "token",
            "secret",
            "password",
            "credential",
            "authorization",
            "bearer",
            "payload",
            "operation",
            "operations",
            "stock",
            "production",
            "purchase",
            "recipe",
            "cost",
            "margin",
            "order",
            "menu",
            "inventory",
            "ingredient",
            "consumption");

    private final Map<String, String> values;

    private AuditMetadata(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static AuditMetadata empty() {
        return new AuditMetadata(Map.of());
    }

    public static AuditMetadata of(Map<String, String> values) {
        Objects.requireNonNull(values, "values must not be null");
        Map<String, String> normalized = values.entrySet().stream()
                .map(entry -> Map.entry(
                        normalizeKey(entry.getKey()),
                        requireValue(entry.getValue())))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
        return new AuditMetadata(normalized);
    }

    private static String normalizeKey(String key) {
        Objects.requireNonNull(key, "metadata key must not be null");
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("metadata key must not be blank");
        }
        if (FORBIDDEN_KEY_MARKERS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException(
                    "metadata key is not allowed for administrative audit");
        }
        return normalized;
    }

    private static String requireValue(String value) {
        Objects.requireNonNull(value, "metadata value must not be null");
        return value;
    }

    public Map<String, String> values() {
        return values;
    }
}
