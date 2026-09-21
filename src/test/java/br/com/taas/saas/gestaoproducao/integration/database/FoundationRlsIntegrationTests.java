package br.com.taas.saas.gestaoproducao.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class FoundationRlsIntegrationTests {

    private static final String TENANT_A = "00000000-0000-0000-0000-000000000001";
    private static final String TENANT_B = "00000000-0000-0000-0000-000000000002";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void deniesRowsWithoutTenantContext() {
        assertThat(settingsCount()).isZero();
    }

    @Test
    @Transactional
    void exposesOnlyTheCurrentTenantRows() {
        setTenantContext(TENANT_A);
        assertThat(settingsCount()).isOne();

        setTenantContext(TENANT_B);
        assertThat(settingsCount()).isOne();
    }

    @Test
    @Transactional
    void deniesInsertForAnotherTenant() {
        setTenantContext(TENANT_A);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO operations.tenant_settings (tenant_id) VALUES (?)",
                TENANT_B))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void deniesMovingAVisibleRowToAnotherTenant() {
        setTenantContext(TENANT_A);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE operations.tenant_settings SET tenant_id = ? WHERE tenant_id = ?",
                TENANT_B,
                TENANT_A))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void runtimeRoleDoesNotBypassRls() {
        Boolean bypassRls = jdbcTemplate.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user",
                Boolean.class);

        assertThat(bypassRls).isFalse();
    }

    private long settingsCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operations.tenant_settings",
                Long.class);
    }

    private void setTenantContext(String tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)",
                String.class,
                tenantId);
    }
}
