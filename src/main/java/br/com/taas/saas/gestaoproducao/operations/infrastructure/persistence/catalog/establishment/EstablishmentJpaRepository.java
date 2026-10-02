package br.com.taas.saas.gestaoproducao.operations.infrastructure.persistence.catalog.establishment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.establishment.Establishment;

public interface EstablishmentJpaRepository extends JpaRepository<Establishment, UUID> {

    Optional<Establishment> findByTenantIdAndNormalizedName(UUID tenantId, String normalizedName);

    Optional<Establishment> findByTenantIdAndId(UUID tenantId, UUID establishmentId);

    List<Establishment> findAllByTenantIdOrderByNormalizedName(UUID tenantId);
}
