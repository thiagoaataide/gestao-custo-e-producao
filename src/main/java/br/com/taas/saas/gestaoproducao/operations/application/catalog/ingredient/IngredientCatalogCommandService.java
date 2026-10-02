package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.IngredientRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.Ingredient;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientName;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

@Service
public class IngredientCatalogCommandService {

    private static final int SIMILAR_CANDIDATE_LIMIT = 10;

    private final AccessDecisionResolver accessDecisionResolver;
    private final TenantScopedTransactionExecutor transactionExecutor;
    private final IngredientRepository ingredients;

    public IngredientCatalogCommandService(AccessDecisionResolver accessDecisionResolver,
            TenantScopedTransactionExecutor transactionExecutor, IngredientRepository ingredients) {
        this.accessDecisionResolver = Objects.requireNonNull(accessDecisionResolver);
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor);
        this.ingredients = Objects.requireNonNull(ingredients);
    }

    public IngredientCommandResult register(ExternalSubject subject, RegisterIngredientCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID tenantId = resolveTenant(subject);
        IngredientName name = new IngredientName(command.name());
        return transactionExecutor.execute(subject, () -> {
            ensureNameAvailable(tenantId, name, null);
            Ingredient ingredient = Ingredient.register(UUID.randomUUID(), tenantId,
                    command.name(), command.baseUnit(), Instant.now());
            List<IngredientView> similar = similarCandidates(tenantId, name);
            Ingredient saved = ingredients.save(ingredient);
            return new IngredientCommandResult(IngredientView.from(saved), similar);
        });
    }

    public IngredientCommandResult rename(ExternalSubject subject, RenameIngredientCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID tenantId = resolveTenant(subject);
        IngredientName name = new IngredientName(command.newName());
        return transactionExecutor.execute(subject, () -> {
            Ingredient ingredient = ingredients.findByTenantIdAndId(tenantId, command.ingredientId())
                    .orElseThrow(() -> new IngredientNotFoundException(command.ingredientId()));
            ensureNameAvailable(tenantId, name, ingredient.id());
            ingredient.rename(command.newName());
            List<IngredientView> similar = similarCandidates(tenantId, name);
            return new IngredientCommandResult(IngredientView.from(ingredients.save(ingredient)), similar);
        });
    }

    private void ensureNameAvailable(UUID tenantId, IngredientName name, UUID currentIngredientId) {
        ingredients.findByTenantIdAndName(tenantId, name)
                .filter(existing -> !existing.id().equals(currentIngredientId))
                .ifPresent(existing -> { throw new IngredientNameAlreadyRegisteredException(name.displayName()); });
    }

    private List<IngredientView> similarCandidates(UUID tenantId, IngredientName name) {
        return ingredients.findSimilarByTenantId(tenantId, name, SIMILAR_CANDIDATE_LIMIT)
                .stream().map(IngredientView::from).toList();
    }

    private UUID resolveTenant(ExternalSubject subject) {
        AccessDecision decision = accessDecisionResolver.resolve(Objects.requireNonNull(subject));
        if (decision.type() != AccessDecisionType.TENANT_ACCESS) {
            throw new TenantAccessDeniedException(decision.type());
        }
        return decision.tenantContext().tenantId().value();
    }
}
