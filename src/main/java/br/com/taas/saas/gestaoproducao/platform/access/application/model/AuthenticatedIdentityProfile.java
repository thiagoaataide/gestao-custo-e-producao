package br.com.taas.saas.gestaoproducao.platform.access.application.model;

import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

/**
 * Provider-neutral profile fields trusted by the platform provisioning flow.
 */
public record AuthenticatedIdentityProfile(
        ExternalSubject subject,
        NormalizedEmail email,
        boolean emailVerified) {

    public AuthenticatedIdentityProfile {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(email, "email must not be null");
    }
}
