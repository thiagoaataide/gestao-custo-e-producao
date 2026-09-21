package br.com.taas.saas.gestaoproducao.platform.identity.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.ExternalIdentityJpaRepository;

@Repository
public class JpaExternalIdentityRepository implements ExternalIdentityRepository {

    private final ExternalIdentityJpaRepository repository;

    public JpaExternalIdentityRepository(ExternalIdentityJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ExternalIdentity> findByProviderAndExternalSubject(ExternalSubject subject) {
        return repository
                .findByProviderAndExternalSubject(subject.provider(), subject.value())
                .map(entity -> entity.toDomain());
    }
}
