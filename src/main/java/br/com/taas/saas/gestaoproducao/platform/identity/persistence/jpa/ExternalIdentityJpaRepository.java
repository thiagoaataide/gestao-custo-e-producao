package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityJpaRepository
        extends JpaRepository<ExternalIdentityJpaEntity, UUID> {

    Optional<ExternalIdentityJpaEntity> findByProviderAndExternalSubject(
            String provider,
            String externalSubject);
}
