package br.com.taas.saas.gestaoproducao.integration.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

@SpringBootTest
@ActiveProfiles("test")
class TenantScopedTransactionExecutorIntegrationTests {

    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ExternalSubject SUBJECT_A =
            ExternalSubject.fromSupabase("test-subject-a");
    private static final ExternalSubject BLOCKED_SUBJECT =
            ExternalSubject.fromSupabase("blocked-subject");

    @Autowired
    private TenantScopedTransactionExecutor transactionExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allowsAProvisionedSubjectToExecuteWithItsResolvedTenantContext() {
        List<UUID> visibleTenants = transactionExecutor.execute(SUBJECT_A, () ->
                jdbcTemplate.query(
                        "SELECT tenant_id FROM operations.tenant_settings ORDER BY tenant_id",
                        (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class)));

        assertThat(visibleTenants).containsExactly(TENANT_A);
    }

    @Test
    void blocksAnUnprovisionedSubjectBeforeTheTenantOperationStarts() {
        AtomicBoolean operationStarted = new AtomicBoolean();

        assertThatThrownBy(() -> transactionExecutor.execute(BLOCKED_SUBJECT, () -> {
            operationStarted.set(true);
            return null;
        }))
                .isInstanceOf(TenantAccessDeniedException.class)
                .satisfies(exception -> assertThat(
                        ((TenantAccessDeniedException) exception).decisionType())
                        .isEqualTo(AccessDecisionType.NOT_PROVISIONED));

        assertThat(operationStarted).isFalse();
    }

    @Test
    void commitsACompletedTenantScopedOperation() {
        OffsetDateTime originalCreatedAt = createdAtForTenant();
        OffsetDateTime committedCreatedAt = originalCreatedAt.plusSeconds(1);

        try {
            transactionExecutor.execute(SUBJECT_A, () -> {
                int updatedRows = jdbcTemplate.update(
                        "UPDATE operations.tenant_settings SET created_at = ? WHERE tenant_id = ?",
                        committedCreatedAt,
                        TENANT_A);
                assertThat(updatedRows).isOne();
                return null;
            });

            assertThat(createdAtForTenant()).isEqualTo(committedCreatedAt);
        } finally {
            transactionExecutor.execute(SUBJECT_A, () -> {
                jdbcTemplate.update(
                        "UPDATE operations.tenant_settings SET created_at = ? WHERE tenant_id = ?",
                        originalCreatedAt,
                        TENANT_A);
                return null;
            });
        }
    }

    @Test
    void rollsBackAllChangesWhenALaterStepFails() {
        OffsetDateTime originalCreatedAt = createdAtForTenant();
        OffsetDateTime uncommittedCreatedAt = originalCreatedAt.plusSeconds(2);

        assertThatThrownBy(() -> transactionExecutor.execute(SUBJECT_A, () -> {
            jdbcTemplate.update(
                    "UPDATE operations.tenant_settings SET created_at = ? WHERE tenant_id = ?",
                    uncommittedCreatedAt,
                    TENANT_A);
            throw new SimulatedUseCaseFailure();
        })).isInstanceOf(SimulatedUseCaseFailure.class);

        assertThat(createdAtForTenant()).isEqualTo(originalCreatedAt);
    }

    private OffsetDateTime createdAtForTenant() {
        return transactionExecutor.execute(SUBJECT_A, () -> jdbcTemplate.queryForObject(
                "SELECT created_at FROM operations.tenant_settings WHERE tenant_id = ?",
                OffsetDateTime.class,
                TENANT_A));
    }

    private static final class SimulatedUseCaseFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}
