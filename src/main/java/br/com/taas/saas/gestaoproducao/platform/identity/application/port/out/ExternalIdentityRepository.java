package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.util.Optional;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

public interface ExternalIdentityRepository {

    Optional<ExternalIdentity> findByProviderAndExternalSubject(ExternalSubject subject);
}
