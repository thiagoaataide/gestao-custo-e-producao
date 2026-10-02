package br.com.taas.saas.gestaoproducao.operations.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.establishment.Establishment;

public interface EstablishmentRepository {

    Optional<Establishment> findByTenantIdAndName(UUID tenantId, String normalizedName);

    Optional<Establishment> findByTenantIdAndId(UUID tenantId, UUID establishmentId);

    List<Establishment> findAllByTenantId(UUID tenantId);

    Establishment save(Establishment establishment);
}
