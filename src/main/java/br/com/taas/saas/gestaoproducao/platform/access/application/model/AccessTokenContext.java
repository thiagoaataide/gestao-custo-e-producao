package br.com.taas.saas.gestaoproducao.platform.access.application.model;

import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

/**
 * Access token context created only after the resource server validates the
 * bearer token cryptographically and semantically.
 */
public record AccessTokenContext(
        ExternalSubject subject,
        String accessToken) {

    public AccessTokenContext {
        Objects.requireNonNull(subject, "subject must not be null");
        accessToken = requireText(accessToken, "accessToken");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
