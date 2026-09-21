package br.com.taas.saas.gestaoproducao.platform.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.TenantJpaRepository;

@Repository
public class JpaTenantRepository implements TenantRepository {

    private final TenantJpaRepository repository;

    public JpaTenantRepository(TenantJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Tenant> findById(UUID tenantId) {
        return repository.findById(tenantId).map(entity -> entity.toDomain());
    }
}
