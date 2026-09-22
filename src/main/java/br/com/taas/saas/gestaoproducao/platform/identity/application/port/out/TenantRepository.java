package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;

public interface TenantRepository {

    Optional<Tenant> findById(UUID tenantId);

    default List<Tenant> findAll() {
        return List.of();
    }

    default Tenant save(Tenant tenant) {
        throw new UnsupportedOperationException("tenant persistence is not available");
    }
}
