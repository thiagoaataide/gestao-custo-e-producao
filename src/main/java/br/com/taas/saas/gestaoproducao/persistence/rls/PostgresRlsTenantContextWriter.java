package br.com.taas.saas.gestaoproducao.persistence.rls;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import br.com.taas.saas.gestaoproducao.tenancy.application.port.out.TenantContextWriter;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantId;

/**
 * PostgreSQL adapter for the transaction-local RLS tenant context.
 */
@Component
public class PostgresRlsTenantContextWriter implements TenantContextWriter {

    private static final String SET_TENANT_CONTEXT_SQL =
            "SELECT set_config('app.tenant_id', ?, true)";

    private final DataSource dataSource;

    public PostgresRlsTenantContextWriter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public void apply(TenantId tenantId, Connection connection) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(connection, "connection must not be null");
        assertTransactionBound(connection);

        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_CONTEXT_SQL)) {
            statement.setString(1, tenantId.value().toString());
            statement.executeQuery();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not apply the RLS tenant context", exception);
        }
    }

    private void assertTransactionBound(Connection connection) {
        try {
            if (connection.isClosed()) {
                throw new IllegalStateException("RLS tenant context requires an open connection");
            }
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("RLS tenant context requires an active transaction");
            }
            if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                throw new IllegalStateException(
                        "RLS tenant context requires the transaction-bound datasource connection");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not validate the RLS transaction connection", exception);
        }
    }
}
