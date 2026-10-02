package br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.IngredientRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.Ingredient;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientBaseUnit;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientName;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantUseCase;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantAccessContext;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantId;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;

class IngredientCatalogCommandServiceTests {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID IDENTITY_ID = UUID.randomUUID();
    private static final ExternalSubject SUBJECT = ExternalSubject.fromSupabase("unit-test-subject");

    private final AccessDecisionResolver accessResolver = mock(AccessDecisionResolver.class);
    private final TenantScopedTransactionExecutor transactionExecutor = mock(TenantScopedTransactionExecutor.class);
    private final IngredientRepository ingredients = mock(IngredientRepository.class);
    private IngredientCatalogCommandService commands;

    @BeforeEach
    void setUp() {
        commands = new IngredientCatalogCommandService(accessResolver, transactionExecutor, ingredients);
        TenantAccessContext context = new TenantAccessContext(SUBJECT, IDENTITY_ID,
                new TenantId(TENANT_ID), MembershipRole.TENANT_USER);
        when(accessResolver.resolve(SUBJECT)).thenReturn(AccessDecision.tenantAccess(SUBJECT, context));
        doAnswer(invocation -> ((TenantUseCase<?>) invocation.getArgument(1)).execute())
                .when(transactionExecutor).execute(eq(SUBJECT), any(TenantUseCase.class));
    }

    @Test
    void duplicateExactNameIsRejectedBeforePersistence() {
        Ingredient existing = ingredient("Farinha de trigo", IngredientBaseUnit.GRAM);
        when(ingredients.findByTenantIdAndName(TENANT_ID, new IngredientName("FARINHA DE TRIGO")))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> commands.register(SUBJECT,
                new RegisterIngredientCommand("FARINHA DE TRIGO", IngredientBaseUnit.GRAM)))
                .isInstanceOf(IngredientNameAlreadyRegisteredException.class);

        verify(ingredients, never()).save(any());
        verify(transactionExecutor).execute(eq(SUBJECT), any(TenantUseCase.class));
    }

    @Test
    void similarCandidateIsReturnedAsAdvisoryAndRegistrationContinues() {
        Ingredient existing = ingredient("Farinha integral", IngredientBaseUnit.GRAM);
        when(ingredients.findByTenantIdAndName(TENANT_ID, new IngredientName("Farinha integral premium")))
                .thenReturn(Optional.empty());
        when(ingredients.findSimilarByTenantId(TENANT_ID,
                new IngredientName("Farinha integral premium"), 10)).thenReturn(List.of(existing));
        when(ingredients.save(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngredientCommandResult result = commands.register(SUBJECT,
                new RegisterIngredientCommand("Farinha integral premium", IngredientBaseUnit.GRAM));

        assertThat(result.similarCandidates()).extracting(IngredientView::id).containsExactly(existing.id());
        assertThat(result.ingredient().id()).isNotEqualTo(existing.id());
        verify(ingredients).save(any(Ingredient.class));
    }

    @Test
    void renameUsesDomainOperationAndPreservesIdentityAndUnit() {
        Ingredient ingredient = ingredient("Leite", IngredientBaseUnit.MILLILITER);
        when(ingredients.findByTenantIdAndId(TENANT_ID, ingredient.id())).thenReturn(Optional.of(ingredient));
        when(ingredients.findByTenantIdAndName(TENANT_ID, new IngredientName("Leite integral")))
                .thenReturn(Optional.empty());
        when(ingredients.findSimilarByTenantId(TENANT_ID, new IngredientName("Leite integral"), 10))
                .thenReturn(List.of());
        when(ingredients.save(ingredient)).thenReturn(ingredient);

        IngredientView result = commands.rename(SUBJECT,
                new RenameIngredientCommand(ingredient.id(), "Leite integral")).ingredient();

        assertThat(result.id()).isEqualTo(ingredient.id());
        assertThat(result.name()).isEqualTo("Leite integral");
        assertThat(result.baseUnit()).isEqualTo("ml");
        verify(ingredients).save(ingredient);
    }

    @Test
    void nonTenantAccessIsDeniedBeforeTheTransactionStarts() {
        when(accessResolver.resolve(SUBJECT)).thenReturn(AccessDecision.platformAccess(SUBJECT));

        assertThatThrownBy(() -> commands.register(SUBJECT,
                new RegisterIngredientCommand("Açúcar cristal", IngredientBaseUnit.GRAM)))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(transactionExecutor, never()).execute(eq(SUBJECT), any(TenantUseCase.class));
        verify(ingredients, never()).save(any());
    }

    private Ingredient ingredient(String name, IngredientBaseUnit unit) {
        return Ingredient.register(UUID.randomUUID(), TENANT_ID, name, unit, Instant.now());
    }
}
