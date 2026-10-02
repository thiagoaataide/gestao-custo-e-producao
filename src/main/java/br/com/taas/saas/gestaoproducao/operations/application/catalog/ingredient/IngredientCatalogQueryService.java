package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.IngredientRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientName;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

@Service
public class IngredientCatalogQueryService {

    private final AccessDecisionResolver accessDecisionResolver;
    private final TenantScopedTransactionExecutor transactionExecutor;
    private final IngredientRepository ingredients;

    public IngredientCatalogQueryService(AccessDecisionResolver accessDecisionResolver,
            TenantScopedTransactionExecutor transactionExecutor, IngredientRepository ingredients) {
        this.accessDecisionResolver = Objects.requireNonNull(accessDecisionResolver);
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor);
        this.ingredients = Objects.requireNonNull(ingredients);
    }

    public List<IngredientView> findSimilar(ExternalSubject subject, String name, int limit) {
        UUID tenantId = resolveTenant(subject);
        IngredientName candidateName = new IngredientName(name);
        return transactionExecutor.execute(subject, () -> ingredients
                .findSimilarByTenantId(tenantId, candidateName, limit)
                .stream().map(IngredientView::from).toList());
    }

    private UUID resolveTenant(ExternalSubject subject) {
        AccessDecision decision = accessDecisionResolver.resolve(Objects.requireNonNull(subject));
        if (decision.type() != AccessDecisionType.TENANT_ACCESS) {
            throw new TenantAccessDeniedException(decision.type());
        }
        return decision.tenantContext().tenantId().value();
    }
}
