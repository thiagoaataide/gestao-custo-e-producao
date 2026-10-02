package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ActiveProfiles("test")
class OperationalManagerAssignmentMigrationIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void assignsOnlyAnActiveMembershipAndPreventsDuplicateActiveAssignments() {
        Membership membership = createMembership("ACTIVE");

        insertAssignment(membership);

        assertThat(assignmentStatus(membership.id())).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> insertAssignment(membership))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsPendingMembershipAndMembershipFromAnotherTenant() {
        Membership pending = createMembership("PENDING");

        assertThatThrownBy(() -> insertAssignment(pending))
                .isInstanceOf(DataAccessException.class);

        Membership active = createMembership("ACTIVE");
        Membership wrongTenant = new Membership(active.id(), UUID.randomUUID(), active.actorId());
        assertThatThrownBy(() -> insertAssignment(wrongTenant))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void keepsRevocationAndAuditHistory() {
        Membership membership = createMembership("ACTIVE");
        UUID assignmentId = insertAssignment(membership);
        insertAuditEvent(membership.actorId(), assignmentId, "OPERATIONAL_MANAGER_ASSIGNED");

        jdbcTemplate.update("""
                UPDATE platform.operational_manager_assignment
                   SET status = 'REVOKED', revoked_by = ?, revoked_at = ?
                 WHERE id = ?
                """, membership.actorId(), Timestamp.from(Instant.now()), assignmentId);
        insertAuditEvent(membership.actorId(), assignmentId, "OPERATIONAL_MANAGER_REVOKED");

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM platform.operational_manager_assignment WHERE id = ?",
                String.class,
                assignmentId);
        long assignmentAuditCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM platform.audit_event
                 WHERE target_id = ? AND action = 'OPERATIONAL_MANAGER_ASSIGNED'
                """, Long.class, assignmentId);
        long revocationAuditCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM platform.audit_event
                 WHERE target_id = ? AND action = 'OPERATIONAL_MANAGER_REVOKED'
                """, Long.class, assignmentId);

        assertThat(persistedStatus).isEqualTo("REVOKED");
        assertThat(assignmentAuditCount).isOne();
        assertThat(revocationAuditCount).isOne();
    }

    private Membership createMembership(String status) {
        UUID identityId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO platform.external_identity (id, provider, external_subject)
                VALUES (?, 'SUPABASE', ?)
                """, identityId, "f02-assignment-" + identityId);
        jdbcTemplate.update("INSERT INTO platform.tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("""
                INSERT INTO platform.membership (id, identity_id, tenant_id, status, role)
                VALUES (?, ?, ?, ?, 'TENANT_USER')
                """, membershipId, identityId, tenantId, status);

        return new Membership(membershipId, tenantId, identityId);
    }

    private UUID insertAssignment(Membership membership) {
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO platform.operational_manager_assignment
                    (id, tenant_id, membership_id, assigned_by)
                VALUES (?, ?, ?, ?)
                """, assignmentId, membership.tenantId(), membership.id(), membership.actorId());
        return assignmentId;
    }

    private String assignmentStatus(UUID membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                  FROM platform.operational_manager_assignment
                 WHERE membership_id = ?
                """, String.class, membershipId);
    }

    private void insertAuditEvent(UUID actorId, UUID assignmentId, String action) {
        jdbcTemplate.update("""
                INSERT INTO platform.audit_event
                    (id, actor_identity_id, action, target_type, target_id, result)
                VALUES (?, ?, ?, 'OPERATIONAL_ASSIGNMENT', ?, 'SUCCESS')
                """, UUID.randomUUID(), actorId, action, assignmentId);
    }

    private record Membership(UUID id, UUID tenantId, UUID actorId) { }
}
