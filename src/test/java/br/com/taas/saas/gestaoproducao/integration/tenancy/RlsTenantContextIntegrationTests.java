package br.com.taas.saas.gestaoproducao.integration.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import br.com.taas.saas.gestaoproducao.tenancy.application.port.out.TenantContextWriter;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantId;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class RlsTenantContextIntegrationTests {

    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private TenantContextWriter tenantContextWriter;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void rejectsAConnectionThatIsNotBoundToAnActiveTransaction() throws SQLException {
        Connection connection = DataSourceUtils.getConnection(dataSource);

        try {
            assertThatThrownBy(() -> tenantContextWriter.apply(tenant(TENANT_A), connection))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("active transaction");
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Test
    void writesTheResolvedTenantToTheSameTransactionConnection() {
        List<UUID> visibleTenants = inTransaction(connection -> {
            tenantContextWriter.apply(tenant(TENANT_A), connection);
            return visibleTenantIds(connection);
        });

        assertThat(visibleTenants).containsExactly(TENANT_A);
    }

    @Test
    void changesTheVisibleRowsWhenASeparateTransactionUsesAnotherResolvedTenant() {
        List<UUID> tenantAVisibleRows =
                inTransaction(connection -> visibleAfterApplying(connection, TENANT_A));
        List<UUID> tenantBVisibleRows =
                inTransaction(connection -> visibleAfterApplying(connection, TENANT_B));

        assertThat(tenantAVisibleRows).containsExactly(TENANT_A);
        assertThat(tenantBVisibleRows).containsExactly(TENANT_B);
    }

    @Test
    void startsAReuseTransactionWithoutThePreviousTenantContext() {
        int firstBackendPid = inTransaction(connection -> {
            tenantContextWriter.apply(tenant(TENANT_A), connection);
            assertThat(visibleTenantIds(connection)).containsExactly(TENANT_A);
            return backendPid(connection);
        });

        List<UUID> visibleTenants = inTransaction(connection -> {
            assertThat(backendPid(connection)).isEqualTo(firstBackendPid);
            return visibleTenantIds(connection);
        });

        assertThat(visibleTenants).isEmpty();
    }

    private List<UUID> visibleAfterApplying(Connection connection, UUID tenantId) {
        tenantContextWriter.apply(tenant(tenantId), connection);
        return visibleTenantIds(connection);
    }

    private List<UUID> visibleTenantIds(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT tenant_id FROM operations.tenant_settings ORDER BY tenant_id");
                ResultSet resultSet = statement.executeQuery()) {
            var tenantIds = new java.util.ArrayList<UUID>();
            while (resultSet.next()) {
                tenantIds.add(resultSet.getObject(1, UUID.class));
            }
            return tenantIds;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read tenant-scoped settings", exception);
        }
    }

    private int backendPid(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not identify the PostgreSQL connection", exception);
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            return work.execute(connection);
        });
    }

    private TenantId tenant(UUID tenantId) {
        return new TenantId(tenantId);
    }

    @FunctionalInterface
    private interface SqlWork<T> {

        T execute(Connection connection);
    }
}
