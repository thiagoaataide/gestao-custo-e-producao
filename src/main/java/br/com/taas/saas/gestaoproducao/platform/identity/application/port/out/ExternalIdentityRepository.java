package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.util.Optional;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

public interface ExternalIdentityRepository {

    Optional<ExternalIdentity> findByProviderAndExternalSubject(ExternalSubject subject);

    /**
     * Persists an identity when a controlled platform bootstrap first sees it.
     * Read-only adapters used by access resolution may keep the default.
     */
    default ExternalIdentity save(ExternalIdentity identity) {
        throw new UnsupportedOperationException("identity persistence is not available");
    }
}
