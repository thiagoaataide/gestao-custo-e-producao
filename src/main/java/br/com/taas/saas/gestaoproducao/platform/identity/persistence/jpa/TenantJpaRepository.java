package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, UUID> {

    Optional<TenantJpaEntity> findById(UUID tenantId);
}
