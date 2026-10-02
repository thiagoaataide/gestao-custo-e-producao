package br.com.taas.saas.gestaoproducao.operations.infrastructure.persistence.catalog.establishment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.EstablishmentRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.establishment.Establishment;

@Repository
public class JpaEstablishmentRepository implements EstablishmentRepository {

    private final EstablishmentJpaRepository repository;

    public JpaEstablishmentRepository(EstablishmentJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Optional<Establishment> findByTenantIdAndName(UUID tenantId, String normalizedName) {
        return repository.findByTenantIdAndNormalizedName(
                Objects.requireNonNull(tenantId), Objects.requireNonNull(normalizedName));
    }

    @Override
    public Optional<Establishment> findByTenantIdAndId(UUID tenantId, UUID establishmentId) {
        return repository.findByTenantIdAndId(
                Objects.requireNonNull(tenantId), Objects.requireNonNull(establishmentId));
    }

    @Override
    public List<Establishment> findAllByTenantId(UUID tenantId) {
        return repository.findAllByTenantIdOrderByNormalizedName(Objects.requireNonNull(tenantId));
    }

    @Override
    public Establishment save(Establishment establishment) {
        return repository.saveAndFlush(Objects.requireNonNull(establishment));
    }
}
