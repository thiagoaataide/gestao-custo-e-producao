package br.com.taas.saas.gestaoproducao.integration.operations.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.IngredientRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.Ingredient;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientBaseUnit;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientName;

@SpringBootTest
@ActiveProfiles("test")
class IngredientRepositoryIntegrationTests {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private IngredientRepository ingredients;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void exactNameLookupReturnsPersistedTenantIngredient() {
        String marker = UUID.randomUUID().toString();
        Ingredient exact = Ingredient.register(UUID.randomUUID(), TENANT_A,
                "Farinha de trigo " + marker, IngredientBaseUnit.GRAM, Instant.now());
        inTransaction(() -> {
            setTenant(TENANT_A);
            ingredients.save(exact);
        });
        inTransaction(() -> {
            setTenant(TENANT_A);
            assertThat(ingredients.findByTenantIdAndName(TENANT_A,
                    new IngredientName("Farinha de trigo " + marker)))
                    .get().extracting(Ingredient::id).isEqualTo(exact.id());
        });
    }

    @Test
    void similarSearchExcludesExactAndHonorsLimit() {
        String marker = UUID.randomUUID().toString();
        Ingredient exact = Ingredient.register(UUID.randomUUID(), TENANT_A,
                "Farinha " + marker, IngredientBaseUnit.GRAM, Instant.now());
        Ingredient similar = Ingredient.register(UUID.randomUUID(), TENANT_A,
                "Farinha integral " + marker, IngredientBaseUnit.GRAM, Instant.now());
        Ingredient otherSimilar = Ingredient.register(UUID.randomUUID(), TENANT_A,
                "Farinha integral " + marker + " orgânica", IngredientBaseUnit.GRAM, Instant.now());
        inTransaction(() -> {
            setTenant(TENANT_A);
            ingredients.save(exact);
            ingredients.save(similar);
            ingredients.save(otherSimilar);
        });
        inTransaction(() -> {
            setTenant(TENANT_A);
            assertThat(ingredients.findSimilarByTenantId(TENANT_A,
                    new IngredientName("integral " + marker), 10))
                    .extracting(Ingredient::id).containsExactlyInAnyOrder(similar.id(), otherSimilar.id());
            assertThat(ingredients.findSimilarByTenantId(TENANT_A,
                    new IngredientName("integral " + marker), 1)).hasSize(1);
        });
    }

    @Test
    void exactAndSimilarSearchRespectTenantContextEvenWhenAnotherTenantIsRequested() {
        String marker = UUID.randomUUID().toString();
        Ingredient tenantAIngredient = Ingredient.register(UUID.randomUUID(), TENANT_A,
                "Farinha integral " + marker, IngredientBaseUnit.GRAM, Instant.now());
        Ingredient tenantBIngredient = Ingredient.register(UUID.randomUUID(), TENANT_B,
                "Farinha integral " + marker, IngredientBaseUnit.GRAM, Instant.now());
        inTransaction(() -> {
            setTenant(TENANT_A);
            ingredients.save(tenantAIngredient);
        });
        inTransaction(() -> {
            setTenant(TENANT_B);
            ingredients.save(tenantBIngredient);
        });
        inTransaction(() -> {
            setTenant(TENANT_B);
            assertThat(ingredients.findByTenantIdAndName(TENANT_B,
                    new IngredientName("Farinha integral " + marker)))
                    .get().extracting(Ingredient::id).isEqualTo(tenantBIngredient.id());
            assertThat(ingredients.findByTenantIdAndName(TENANT_A,
                    new IngredientName("Farinha integral " + marker))).isEmpty();
            assertThat(ingredients.findSimilarByTenantId(TENANT_B,
                    new IngredientName("integral " + marker), 10))
                    .extracting(Ingredient::id).containsExactly(tenantBIngredient.id());
            assertThat(ingredients.findSimilarByTenantId(TENANT_A,
                    new IngredientName("integral " + marker), 10)).isEmpty();
        });
    }

    @Test
    void similarSearchReturnsEmptyWhenThereAreNoCandidates() {
        inTransaction(() -> {
            setTenant(TENANT_A);
            assertThat(ingredients.findSimilarByTenantId(
                    TENANT_A, new IngredientName("candidato inexistente " + UUID.randomUUID()), 10))
                    .isEmpty();
        });
    }

    private void setTenant(UUID tenantId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class,
                tenantId.toString());
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
