package br.com.taas.saas.gestaoproducao.integration.operations.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

import br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient.IngredientCatalogCommandService;
import br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient.IngredientNameAlreadyRegisteredException;
import br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient.IngredientNotFoundException;
import br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient.RegisterIngredientCommand;
import br.com.taas.saas.gestaoproducao.operations.application.catalog.ingredient.RenameIngredientCommand;
import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.IngredientBaseUnit;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

@SpringBootTest
@ActiveProfiles("test")
class IngredientCatalogCommandIntegrationTests {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ExternalSubject SUBJECT_A = ExternalSubject.fromSupabase("test-subject-a");
    private static final ExternalSubject BLOCKED_SUBJECT = ExternalSubject.fromSupabase("blocked-subject");

    @Autowired
    private IngredientCatalogCommandService commands;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantScopedTransactionExecutor transactionExecutor;

    @Test
    void registrationPersistsIngredientAndBlocksNormalizedDuplicate() {
        String name = "Farinha " + UUID.randomUUID();
        var created = commands.register(SUBJECT_A,
                new RegisterIngredientCommand(name, IngredientBaseUnit.GRAM)).ingredient();

        assertThat(transactionExecutor.execute(SUBJECT_A, () -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operations.ingredient WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_A, created.id()))).isOne();
        assertThatThrownBy(() -> commands.register(SUBJECT_A,
                new RegisterIngredientCommand(name.toUpperCase(), IngredientBaseUnit.GRAM)))
                .isInstanceOf(IngredientNameAlreadyRegisteredException.class);
    }

    @Test
    void similarIngredientIsReturnedAsWarningAndDoesNotBlockRegistration() {
        String marker = UUID.randomUUID().toString();
        var existing = commands.register(SUBJECT_A,
                new RegisterIngredientCommand("Farinha integral " + marker, IngredientBaseUnit.GRAM))
                .ingredient();

        var result = commands.register(SUBJECT_A,
                new RegisterIngredientCommand("Farinha integral " + marker + " premium",
                        IngredientBaseUnit.GRAM));

        assertThat(result.ingredient().id()).isNotEqualTo(existing.id());
        assertThat(result.similarCandidates()).extracting(candidate -> candidate.id())
                .contains(existing.id());
        assertThat(transactionExecutor.execute(SUBJECT_A, () -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operations.ingredient WHERE tenant_id = ? AND display_name LIKE ?",
                Long.class, TENANT_A, "%" + marker + "%"))).isEqualTo(2L);
    }

    @Test
    void renamePreservesIdentityAndBaseUnitAndHidesForeignTenantIds() {
        var original = commands.register(SUBJECT_A,
                new RegisterIngredientCommand("Leite " + UUID.randomUUID(), IngredientBaseUnit.MILLILITER))
                .ingredient();
        String newName = "Leite integral " + UUID.randomUUID();

        var renamed = commands.rename(SUBJECT_A, new RenameIngredientCommand(original.id(), newName)).ingredient();

        assertThat(renamed.id()).isEqualTo(original.id());
        assertThat(renamed.name()).isEqualTo(newName);
        assertThat(renamed.baseUnit()).isEqualTo("ml");
        assertThatThrownBy(() -> commands.rename(SUBJECT_A,
                new RenameIngredientCommand(UUID.randomUUID(), "Nome novo")))
                .isInstanceOf(IngredientNotFoundException.class);
    }

    @Test
    void unprovisionedSubjectCannotRegisterIngredient() {
        String marker = UUID.randomUUID().toString();
        assertThatThrownBy(() -> commands.register(BLOCKED_SUBJECT,
                new RegisterIngredientCommand("Negado " + marker, IngredientBaseUnit.GRAM)))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThat(transactionExecutor.execute(SUBJECT_A, () -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operations.ingredient WHERE display_name LIKE ?",
                Long.class, "%" + marker + "%"))).isZero();
    }
}
