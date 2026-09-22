package br.com.taas.saas.gestaoproducao.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class PlatformProvisioningMigrationIntegrationTests {

    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID IDENTITY_A =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID IDENTITY_B =
            UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void createsPlatformProvisioningTablesAndTenantMetadata() {
        assertThat(tableExists("platform", "platform_role_assignment")).isTrue();
        assertThat(tableExists("platform", "invitation")).isTrue();
        assertThat(tableExists("platform", "audit_event")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM platform.tenant WHERE id = ?",
                String.class,
                TENANT_A)).isEqualTo("Tenant");
    }

    @Test
    @Transactional
    void enforcesOneActiveOwner() {
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID secondOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000302");

        insertRole(ownerId, IDENTITY_A, "PLATFORM_OWNER", "ACTIVE", null);

        assertThatThrownBy(() -> insertRole(
                secondOwnerId,
                IDENTITY_B,
                "PLATFORM_OWNER",
                "ACTIVE",
                null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void enforcesOneActiveAssignmentPerIdentityAndRole() {
        UUID firstAdminId = UUID.fromString("00000000-0000-0000-0000-000000000303");
        UUID secondAdminId = UUID.fromString("00000000-0000-0000-0000-000000000304");

        insertRole(firstAdminId, IDENTITY_A, "PLATFORM_ADMIN", "ACTIVE", null);

        assertThatThrownBy(() -> insertRole(
                secondAdminId,
                IDENTITY_A,
                "PLATFORM_ADMIN",
                "ACTIVE",
                null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void enforcesPendingInvitationUniquenessAndForeignKeys() {
        UUID invitationId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID duplicateId = UUID.fromString("00000000-0000-0000-0000-000000000402");
        Instant createdAt = Instant.parse("2026-09-22T12:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-23T12:00:00Z");

        insertInvitation(invitationId, TENANT_A, "invitee@example.com", createdAt, expiresAt);

        assertThatThrownBy(() -> insertInvitation(
                duplicateId,
                TENANT_A,
                "invitee@example.com",
                createdAt,
                expiresAt))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void enforcesLifecycleStatesAndInvitationTransitionTimestamps() {
        UUID suspendedTenant = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID closedTenant = UUID.fromString("00000000-0000-0000-0000-000000000012");
        jdbcTemplate.update(
                "INSERT INTO platform.tenant (id, name, status) VALUES (?, ?, ?)",
                suspendedTenant,
                "Suspended tenant",
                "SUSPENDED");
        jdbcTemplate.update(
                "INSERT INTO platform.tenant (id, name, status) VALUES (?, ?, ?)",
                closedTenant,
                "Closed tenant",
                "CLOSED");

        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM platform.tenant WHERE id IN (?, ?) ORDER BY id",
                String.class,
                suspendedTenant,
                closedTenant))
                .containsExactly("SUSPENDED", "CLOSED");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO platform.invitation "
                        + "(id, tenant_id, email, token_digest, expires_at, "
                        + "status, accepted_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.fromString("00000000-0000-0000-0000-000000000403"),
                TENANT_B,
                "invalid@example.com",
                "digest-403",
                Timestamp.from(Instant.parse("2026-09-23T12:00:00Z")),
                "ACCEPTED",
                null,
                IDENTITY_A))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void grantsRuntimePlatformAccessWithoutDeleteOrRlsBypass() {
        assertThat(hasTablePrivilege("platform.tenant", "SELECT")).isTrue();
        assertThat(hasTablePrivilege("platform.tenant", "INSERT")).isTrue();
        assertThat(hasTablePrivilege("platform.tenant", "UPDATE")).isTrue();
        assertThat(hasTablePrivilege("platform.tenant", "DELETE")).isFalse();
        assertThat(hasTablePrivilege("platform.audit_event", "INSERT")).isTrue();
        assertThat(hasTablePrivilege("platform.audit_event", "UPDATE")).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user",
                Boolean.class)).isFalse();
    }

    private void insertRole(
            UUID id,
            UUID identityId,
            String role,
            String status,
            Instant revokedAt) {
        jdbcTemplate.update(
                "INSERT INTO platform.platform_role_assignment "
                        + "(id, identity_id, role, status, revoked_at) VALUES (?, ?, ?, ?, ?)",
                id,
                identityId,
                role,
                status,
                revokedAt);
    }

    private void insertInvitation(
            UUID id,
            UUID tenantId,
            String email,
            Instant createdAt,
            Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO platform.invitation "
                        + "(id, tenant_id, email, token_digest, expires_at, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                email,
                "digest-" + id,
                Timestamp.from(expiresAt),
                IDENTITY_A,
                Timestamp.from(createdAt));
    }

    private boolean tableExists(String schema, String table) {
        return jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                schema + "." + table);
    }

    private boolean hasTablePrivilege(String table, String privilege) {
        return jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(current_user, ?, ?)",
                Boolean.class,
                table,
                privilege);
    }
}
