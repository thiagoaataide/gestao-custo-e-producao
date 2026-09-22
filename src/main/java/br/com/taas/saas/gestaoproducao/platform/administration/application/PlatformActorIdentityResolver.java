package br.com.taas.saas.gestaoproducao.platform.administration.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

/** Translates the provider subject into the internal identity used by the domain. */
@Service
public final class PlatformActorIdentityResolver {

    private final ExternalIdentityRepository externalIdentityRepository;

    public PlatformActorIdentityResolver(ExternalIdentityRepository externalIdentityRepository) {
        this.externalIdentityRepository = Objects.requireNonNull(
                externalIdentityRepository,
                "externalIdentityRepository must not be null");
    }

    public UUID requireIdentityId(ExternalSubject subject) {
        Objects.requireNonNull(subject, "subject must not be null");
        return externalIdentityRepository
                .findByProviderAndExternalSubject(subject)
                .filter(ExternalIdentity::isActive)
                .map(ExternalIdentity::id)
                .orElseThrow(PlatformAuthorizationDeniedException::new);
    }
}
