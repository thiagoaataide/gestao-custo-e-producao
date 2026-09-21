package br.com.taas.saas.gestaoproducao.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

/**
 * Crosses the foundation boundaries that are covered individually by the
 * focused integration tests: migration, resolved access, RLS and transaction
 * execution. The test database created here is isolated and removed after the
 * migration idempotency check.
 */
@SpringBootTest
@ActiveProfiles("test")
class FoundationEndToEndIntegrationTests {

    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final ExternalSubject SUBJECT_A =
            ExternalSubject.fromSupabase("test-subject-a");
    private static final ExternalSubject BLOCKED_SUBJECT =
            ExternalSubject.fromSupabase("blocked-subject");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantScopedTransactionExecutor transactionExecutor;

    @Value("${spring.flyway.url}")
    private String migrationUrl;

    @Value("${spring.flyway.user}")
    private String migrationUser;

    @Value("${spring.flyway.password}")
    private String migrationPassword;

    @Test
    void migratesAnEmptyDatabaseOnceAndKeepsTheHistoryIdempotent() throws Exception {
        String databaseName = "f00_t11_" + UUID.randomUUID().toString().replace("-", "");
        String databaseUrl = databaseUrl(databaseName);

        createDatabase(databaseName);
        try {
            assertThat(hasRelation(databaseUrl, "platform.tenant")).isFalse();

            Flyway flyway = Flyway.configure()
                    .dataSource(databaseUrl, migrationUser, migrationPassword)
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .load();
            int expectedMigrationCount = flyway.info().all().length;

            flyway.migrate();
            assertThat(appliedMigrationCount(databaseUrl)).isEqualTo(expectedMigrationCount);

            flyway.migrate();
            assertThat(appliedMigrationCount(databaseUrl)).isEqualTo(expectedMigrationCount);
            assertThat(hasRelation(databaseUrl, "platform.tenant")).isTrue();
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void resolvedTenantCannotBeChangedBySupplyingAnotherTenantAsAQueryParameter() {
        List<UUID> visibleTenants = transactionExecutor.execute(SUBJECT_A, () ->
                jdbcTemplate.query(
                        "SELECT tenant_id FROM operations.tenant_settings WHERE tenant_id = ?",
                        (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                        TENANT_B));

        assertThat(visibleTenants).isEmpty();
    }

    @Test
    void unprovisionedOrBlockedIdentityIsDeniedBeforeTheOperationRuns() {
        assertThatThrownBy(() -> transactionExecutor.execute(
                ExternalSubject.fromSupabase("not-provisioned-subject"),
                () -> null))
                .isInstanceOf(TenantAccessDeniedException.class)
                .satisfies(exception -> assertThat(
                        ((TenantAccessDeniedException) exception).decisionType())
                        .isEqualTo(AccessDecisionType.NOT_PROVISIONED));

        assertThatThrownBy(() -> transactionExecutor.execute(BLOCKED_SUBJECT, () -> null))
                .isInstanceOf(TenantAccessDeniedException.class)
                .satisfies(exception -> assertThat(
                        ((TenantAccessDeniedException) exception).decisionType())
                        .isEqualTo(AccessDecisionType.NOT_PROVISIONED));
    }

    @Test
    void rlsRejectsAWriteForAnotherTenantInsideTheResolvedTransaction() {
        assertThatThrownBy(() -> transactionExecutor.execute(SUBJECT_A, () -> {
            jdbcTemplate.update(
                    "INSERT INTO operations.tenant_settings (tenant_id) VALUES (?)",
                    TENANT_B);
            return null;
        })).isInstanceOf(DataAccessException.class);
    }

    private String databaseUrl(String databaseName) {
        int lastSlash = migrationUrl.lastIndexOf('/');
        return migrationUrl.substring(0, lastSlash + 1) + databaseName;
    }

    private void createDatabase(String databaseName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                databaseUrl("postgres"), migrationUser, migrationPassword);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        }
    }

    private void dropDatabase(String databaseName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                databaseUrl("postgres"), migrationUser, migrationPassword);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS "
                    + quoteIdentifier(databaseName) + " WITH (FORCE)");
        }
    }

    private boolean hasRelation(String databaseUrl, String relationName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                databaseUrl, migrationUser, migrationPassword);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT to_regclass('" + relationName + "')")) {
            resultSet.next();
            return resultSet.getString(1) != null;
        }
    }

    private long appliedMigrationCount(String databaseUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                databaseUrl, migrationUser, migrationPassword);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE success")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
