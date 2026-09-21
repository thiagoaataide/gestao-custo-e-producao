package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface ExternalIdentityJpaRepository extends Repository<ExternalIdentityJpaEntity, UUID> {

    Optional<ExternalIdentityJpaEntity> findByProviderAndExternalSubject(
            String provider,
            String externalSubject);
}
