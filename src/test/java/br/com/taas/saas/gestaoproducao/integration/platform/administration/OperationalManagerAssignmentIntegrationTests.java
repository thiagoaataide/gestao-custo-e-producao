package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.AssignOperationalManagerCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.OperationalManagerAssignmentService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.OperationalManagerAssignmentValidationException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.OperationalManagerAuthorizationQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.RevokeOperationalManagerCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignmentStatus;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.OperationalManagerAssignmentRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

@SpringBootTest
@ActiveProfiles("test")
class OperationalManagerAssignmentIntegrationTests {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID UNAUTHORIZED_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TARGET_IDENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TARGET_MEMBERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PENDING_MEMBERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000204");
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private OperationalManagerAssignmentService assignmentService;

    @Autowired
    private OperationalManagerAssignmentRepository assignmentRepository;

    @Autowired
    private OperationalManagerAuthorizationQuery authorizationQuery;

    @Autowired
    private PlatformRoleRepository platformRoleRepository;

    @Autowired
    private AdministrativeAuditRepository auditRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void assignmentRequiresPlatformAccessAndWritesItsSuccessAudit() {
        grantPlatformAdmin();

        var assignment = assignmentService.assign(
                new AssignOperationalManagerCommand(ACTOR_ID, TARGET_MEMBERSHIP_ID, NOW));
        var audit = audit(ACTOR_ID, assignment.id(), AuditAction.OPERATIONAL_MANAGER_ASSIGNED,
                AuditResult.SUCCESS);

        assertThat(assignment.status()).isEqualTo(OperationalManagerAssignmentStatus.ACTIVE);
        assertThat(assignment.tenantId()).isEqualTo(TENANT_A);
        assertThat(authorizationQuery.isActiveOperationalManager(TARGET_IDENTITY_ID, TENANT_A))
                .isTrue();
        assertThat(audit.content()).hasSize(1);
        assertThat(audit.content().getFirst().targetType())
                .isEqualTo(AuditTargetType.OPERATIONAL_ASSIGNMENT);
    }

    @Test
    @Transactional
    void revocationPersistsHistoryAndRemovesActiveAuthorization() {
        grantPlatformAdmin();
        var assignment = assignmentService.assign(
                new AssignOperationalManagerCommand(ACTOR_ID, TARGET_MEMBERSHIP_ID, NOW));
        Instant revokedAt = NOW.plusSeconds(60);

        var revoked = assignmentService.revoke(
                new RevokeOperationalManagerCommand(ACTOR_ID, assignment.id(), revokedAt));

        assertThat(revoked.status()).isEqualTo(OperationalManagerAssignmentStatus.REVOKED);
        assertThat(revoked.revokedBy()).isEqualTo(ACTOR_ID);
        assertThat(revoked.revokedAt()).isEqualTo(revokedAt);
        assertThat(authorizationQuery.isActiveOperationalManager(TARGET_IDENTITY_ID, TENANT_A))
                .isFalse();
        assertThat(audit(ACTOR_ID, assignment.id(), AuditAction.OPERATIONAL_MANAGER_REVOKED,
                AuditResult.SUCCESS).content()).hasSize(1);
    }

    @Test
    @Transactional
    void deniesAnActorWithoutPlatformAccessAndAuditsTheDenial() {
        assertThatThrownBy(() -> assignmentService.assign(
                new AssignOperationalManagerCommand(UNAUTHORIZED_ACTOR_ID, TARGET_MEMBERSHIP_ID, NOW)))
                .isInstanceOf(PlatformAuthorizationDeniedException.class);

        var deniedEvents = audit(UNAUTHORIZED_ACTOR_ID, null,
                AuditAction.OPERATIONAL_MANAGER_ASSIGNED, AuditResult.DENIED);
        assertThat(deniedEvents.content()).hasSize(1);
        assertThat(assignmentRepository.findActiveByMembershipId(TARGET_MEMBERSHIP_ID)).isEmpty();
    }

    @Test
    @Transactional
    void rejectsPendingMembershipWithoutCreatingAnAssignment() {
        grantPlatformAdmin();

        assertThatThrownBy(() -> assignmentService.assign(
                new AssignOperationalManagerCommand(ACTOR_ID, PENDING_MEMBERSHIP_ID, NOW)))
                .isInstanceOf(OperationalManagerAssignmentValidationException.class);

        assertThat(assignmentRepository.findActiveByMembershipId(PENDING_MEMBERSHIP_ID)).isEmpty();
        assertThat(audit(ACTOR_ID, null, AuditAction.OPERATIONAL_MANAGER_ASSIGNED,
                AuditResult.FAILED).content()).hasSize(1);
    }

    @Test
    @Transactional
    void rejectsASecondActiveAssignmentAndKeepsTheOriginal() {
        grantPlatformAdmin();
        var original = assignmentService.assign(
                new AssignOperationalManagerCommand(ACTOR_ID, TARGET_MEMBERSHIP_ID, NOW));

        assertThatThrownBy(() -> assignmentService.assign(
                new AssignOperationalManagerCommand(ACTOR_ID, TARGET_MEMBERSHIP_ID, NOW.plusSeconds(1))))
                .isInstanceOf(OperationalManagerAssignmentValidationException.class);

        assertThat(assignmentRepository.findActiveByMembershipId(TARGET_MEMBERSHIP_ID))
                .get()
                .extracting(assignment -> assignment.id())
                .isEqualTo(original.id());
        assertThat(audit(ACTOR_ID, null, AuditAction.OPERATIONAL_MANAGER_ASSIGNED,
                AuditResult.FAILED).content()).hasSize(1);
    }

    @Test
    @Transactional
    void authorizationQueryRequiresAnActiveMembershipInTheRequestedTenant() {
        grantPlatformAdmin();
        assignmentService.assign(new AssignOperationalManagerCommand(ACTOR_ID, TARGET_MEMBERSHIP_ID, NOW));

        assertThat(authorizationQuery.isActiveOperationalManager(TARGET_IDENTITY_ID, TENANT_B))
                .isFalse();

        jdbcTemplate.update("""
                UPDATE platform.membership
                   SET status = 'REVOKED', revoked_at = ?
                 WHERE id = ?
                """, Timestamp.from(NOW.plusSeconds(30)), TARGET_MEMBERSHIP_ID);

        assertThat(authorizationQuery.isActiveOperationalManager(TARGET_IDENTITY_ID, TENANT_A))
                .isFalse();
    }

    private void grantPlatformAdmin() {
        platformRoleRepository.save(new PlatformRoleAssignment(
                UUID.randomUUID(), ACTOR_ID, PlatformRole.PLATFORM_ADMIN,
                PlatformRoleStatus.ACTIVE, NOW, null));
    }

    private br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage audit(
            UUID actorId, UUID targetId, AuditAction action, AuditResult result) {
        return auditRepository.findPage(new AuditEventQuery(
                actorId, action, AuditTargetType.OPERATIONAL_ASSIGNMENT, targetId,
                result, null, null, 0, 10));
    }
}
