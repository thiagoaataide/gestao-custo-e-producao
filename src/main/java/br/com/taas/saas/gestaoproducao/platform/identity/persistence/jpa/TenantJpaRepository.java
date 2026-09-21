package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface TenantJpaRepository extends Repository<TenantJpaEntity, UUID> {

    Optional<TenantJpaEntity> findById(UUID tenantId);
}
