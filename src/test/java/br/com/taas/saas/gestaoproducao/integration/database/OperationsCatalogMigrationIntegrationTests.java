package br.com.taas.saas.gestaoproducao.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OperationsCatalogMigrationIntegrationTests {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void rlsScopesIngredientsAndEstablishmentsAndRejectsCrossTenantWrites() {
        String marker = UUID.randomUUID().toString();
        inTransaction(() -> {
            setTenant(TENANT_A);
            insertIngredient(TENANT_A, "Açúcar cristal " + marker, "acucar cristal " + marker, "MASS", "g");
            insertEstablishment(TENANT_A, "Mercado Central " + marker, "mercado central " + marker);
        });
        inTransaction(() -> {
            setTenant(TENANT_B);
            assertThat(countByName("operations.ingredient", "normalized_name", marker)).isZero();
            assertThat(countByName("operations.establishment", "normalized_name", marker)).isZero();
            assertThat(jdbcTemplate.update("""
                    UPDATE operations.ingredient SET display_name = 'alterado'
                     WHERE normalized_name = ?
                    """, "acucar cristal " + marker)).isZero();
            assertThat(jdbcTemplate.update("""
                    DELETE FROM operations.ingredient WHERE normalized_name = ?
                    """, "acucar cristal " + marker)).isZero();
            assertThat(jdbcTemplate.update("""
                    UPDATE operations.establishment SET display_name = 'alterado'
                     WHERE normalized_name = ?
                    """, "mercado central " + marker)).isZero();
            assertThat(jdbcTemplate.update("""
                    DELETE FROM operations.establishment WHERE normalized_name = ?
                    """, "mercado central " + marker)).isZero();
        });
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_B);
            insertIngredient(TENANT_A, "Açúcar mascavo " + marker, "acucar mascavo " + marker, "MASS", "g");
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_B);
            insertEstablishment(TENANT_A, "Outro Mercado " + marker, "outro mercado " + marker);
        })).isInstanceOf(DataAccessException.class);
        inTransaction(() -> {
            setTenant(null);
            assertThat(countByName("operations.ingredient", "normalized_name", marker)).isZero();
            assertThat(countByName("operations.establishment", "normalized_name", marker)).isZero();
        });
    }

    @Test
    void normalizedNamesAreUniqueWithinATenantButIsolatedAcrossTenants() {
        String suffix = UUID.randomUUID().toString();
        String normalized = "acucar cristal " + suffix;
        String establishmentNormalized = "mercado central " + suffix;
        inTransaction(() -> {
            setTenant(TENANT_A);
            insertIngredient(TENANT_A, "Açúcar cristal " + suffix, normalized, "MASS", "g");
            insertEstablishment(TENANT_A, "Mercado Central " + suffix, establishmentNormalized);
        });
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            insertIngredient(TENANT_A, "Acucar cristal " + suffix, normalized, "MASS", "g");
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            insertEstablishment(TENANT_A, "MERCADO CENTRAL " + suffix, establishmentNormalized);
        })).isInstanceOf(DataAccessException.class);
        inTransaction(() -> {
            setTenant(TENANT_B);
            insertIngredient(TENANT_B, "Açúcar cristal " + suffix, normalized, "MASS", "g");
            insertEstablishment(TENANT_B, "Mercado Central " + suffix, establishmentNormalized);
            assertThat(countByName("operations.ingredient", "normalized_name", suffix)).isOne();
            assertThat(countByName("operations.establishment", "normalized_name", suffix)).isOne();
        });
    }

    @Test
    void ingredientUnitsMustMatchTheirQuantityDimensionAndTenantMustExist() {
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            insertIngredient(TENANT_A, unique("Açúcar"), unique("acucar"), "MASS", "ml");
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            insertIngredient(TENANT_A, unique("Açúcar"), unique("acucar"), "MASS", "kg");
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            insertIngredient(UUID.randomUUID(), unique("Açúcar"), unique("acucar"), "MASS", "g");
        })).isInstanceOf(DataAccessException.class);

        inTransaction(() -> {
            setTenant(TENANT_A);
            String suffix = UUID.randomUUID().toString();
            insertIngredient(TENANT_A, "Açúcar " + suffix, "acucar " + suffix, "MASS", "g");
            insertIngredient(TENANT_A, "Leite " + suffix, "leite " + suffix, "VOLUME", "ml");
            insertIngredient(TENANT_A, "Ovo " + suffix, "ovo " + suffix, "COUNT", "un");
            assertThat(countByName("operations.ingredient", "normalized_name", suffix)).isEqualTo(3);
        });
    }

    @Test
    void tenantOperationalZoneHasPilotDefaultAndAcceptsNonblankOverride() {
        inTransaction(() -> {
            setTenant(TENANT_A);
            String defaultZone = jdbcTemplate.queryForObject(
                    "SELECT operational_zone_id FROM operations.tenant_settings WHERE tenant_id = ?",
                    String.class, TENANT_A);
            assertThat(defaultZone).isEqualTo("America/Sao_Paulo");
            jdbcTemplate.update("UPDATE operations.tenant_settings SET operational_zone_id = ? WHERE tenant_id = ?",
                    "America/Manaus", TENANT_A);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT operational_zone_id FROM operations.tenant_settings WHERE tenant_id = ?",
                    String.class, TENANT_A)).isEqualTo("America/Manaus");
        });
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            jdbcTemplate.update("UPDATE operations.tenant_settings SET operational_zone_id = ? WHERE tenant_id = ?",
                    "   ", TENANT_A);
        })).isInstanceOf(DataAccessException.class);
    }

    private void setTenant(UUID tenantId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class,
                tenantId == null ? "" : tenantId.toString());
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private String unique(String value) {
        return value + " " + UUID.randomUUID();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long countByName(String table, String column, String marker) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " LIKE ?",
                Long.class, "%" + marker);
    }

    private void insertIngredient(
            UUID tenantId, String name, String normalizedName, String dimension, String unit) {
        jdbcTemplate.update("""
                INSERT INTO operations.ingredient
                    (id, tenant_id, display_name, normalized_name, quantity_dimension, base_unit)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, name, normalizedName, dimension, unit);
    }

    private void insertEstablishment(UUID tenantId, String name, String normalizedName) {
        jdbcTemplate.update("""
                INSERT INTO operations.establishment (id, tenant_id, display_name, normalized_name)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, name, normalizedName);
    }
}
