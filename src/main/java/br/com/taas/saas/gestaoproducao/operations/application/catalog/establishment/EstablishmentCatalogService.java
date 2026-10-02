package br.com.taas.saas.gestaoproducao.operations.application.catalog.establishment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.EstablishmentRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.establishment.Establishment;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

@Service
public class EstablishmentCatalogService {

    private final AccessDecisionResolver accessDecisionResolver;
    private final TenantScopedTransactionExecutor transactionExecutor;
    private final EstablishmentRepository establishments;

    public EstablishmentCatalogService(AccessDecisionResolver accessDecisionResolver,
            TenantScopedTransactionExecutor transactionExecutor, EstablishmentRepository establishments) {
        this.accessDecisionResolver = Objects.requireNonNull(accessDecisionResolver);
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor);
        this.establishments = Objects.requireNonNull(establishments);
    }

    public EstablishmentView register(ExternalSubject subject, RegisterEstablishmentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID tenantId = resolveTenant(subject);
        Establishment establishment = Establishment.register(
                UUID.randomUUID(), tenantId, command.name(), Instant.now());
        return transactionExecutor.execute(subject, () -> {
            establishments.findByTenantIdAndName(tenantId, establishment.normalizedName())
                    .ifPresent(existing -> {
                        throw new EstablishmentNameAlreadyRegisteredException(establishment.displayName());
                    });
            return EstablishmentView.from(establishments.save(establishment));
        });
    }

    public List<EstablishmentView> findAll(ExternalSubject subject) {
        UUID tenantId = resolveTenant(subject);
        return transactionExecutor.execute(subject, () -> establishments.findAllByTenantId(tenantId)
                .stream().map(EstablishmentView::from).toList());
    }

    private UUID resolveTenant(ExternalSubject subject) {
        AccessDecision decision = accessDecisionResolver.resolve(Objects.requireNonNull(subject));
        if (decision.type() != AccessDecisionType.TENANT_ACCESS) {
            throw new TenantAccessDeniedException(decision.type());
        }
        return decision.tenantContext().tenantId().value();
    }
}
