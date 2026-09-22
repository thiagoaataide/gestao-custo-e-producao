package br.com.taas.saas.gestaoproducao.integration.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.PlatformRoleAssignmentConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

@SpringBootTest
@ActiveProfiles("test")
class PlatformRolePersistenceIntegrationTests {

    private static final UUID IDENTITY_A =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID IDENTITY_B =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private PlatformRoleRepository platformRoleRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void persistsAndFindsActiveRolesByInternalIdentity() {
        UUID assignmentId = UUID.randomUUID();
        PlatformRoleAssignment assignment = activeAssignment(
                assignmentId,
                IDENTITY_A,
                PlatformRole.PLATFORM_ADMIN);

        PlatformRoleAssignment saved = platformRoleRepository.save(assignment);

        assertThat(saved.id()).isEqualTo(assignmentId);
        assertThat(saved.identityId()).isEqualTo(IDENTITY_A);
        assertThat(saved.role()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(saved.status()).isEqualTo(PlatformRoleStatus.ACTIVE);
        var activeAssignments = platformRoleRepository.findActiveByIdentityId(IDENTITY_A);
        assertThat(activeAssignments).hasSize(1);
        assertThat(activeAssignments.getFirst().id()).isEqualTo(saved.id());
        assertThat(activeAssignments.getFirst().role()).isEqualTo(saved.role());
    }

    @Test
    @Transactional
    void findsTheSingleActiveOwner() {
        PlatformRoleAssignment owner = activeAssignment(
                UUID.randomUUID(),
                IDENTITY_A,
                PlatformRole.PLATFORM_OWNER);

        platformRoleRepository.save(owner);

        var activeOwner = platformRoleRepository.findActiveOwner().orElseThrow();
        assertThat(activeOwner.id()).isEqualTo(owner.id());
        assertThat(activeOwner.identityId()).isEqualTo(owner.identityId());
        assertThat(activeOwner.role()).isEqualTo(PlatformRole.PLATFORM_OWNER);
    }

    @Test
    @Transactional
    void translatesDuplicateActiveOwnerIntoPredictableConflict() {
        platformRoleRepository.save(activeAssignment(
                UUID.randomUUID(),
                IDENTITY_A,
                PlatformRole.PLATFORM_OWNER));

        assertThatThrownBy(() -> platformRoleRepository.save(activeAssignment(
                UUID.randomUUID(),
                IDENTITY_B,
                PlatformRole.PLATFORM_OWNER)))
                .isInstanceOf(PlatformRoleAssignmentConflictException.class)
                .hasMessageContaining("conflicts");
    }

    @Test
    @Transactional
    void revocationPreservesHistoryAndDoesNotChangeTenantMembership() {
        PlatformRoleAssignment active = activeAssignment(
                UUID.randomUUID(),
                IDENTITY_A,
                PlatformRole.PLATFORM_ADMIN);
        platformRoleRepository.save(active);

        var membershipBefore = membershipRepository.findActiveByIdentityId(IDENTITY_A);
        PlatformRoleAssignment revoked = active.revoke(Instant.parse("2026-01-01T12:00:00Z"));

        platformRoleRepository.save(revoked);

        assertThat(platformRoleRepository.findActiveByIdentityId(IDENTITY_A)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.platform_role_assignment WHERE id = ?",
                String.class,
                active.id())).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM platform.platform_role_assignment WHERE id = ?",
                Instant.class,
                active.id())).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));

        var membershipAfter = membershipRepository.findActiveByIdentityId(IDENTITY_A);
        assertThat(membershipAfter).hasSize(1);
        assertThat(membershipAfter.getFirst().tenantId()).isEqualTo(TENANT_A);
        assertThat(membershipAfter.getFirst().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membershipAfter).hasSize(membershipBefore.size());
        assertThat(membershipAfter.getFirst().id()).isEqualTo(membershipBefore.getFirst().id());
        assertThat(membershipAfter.getFirst().tenantId())
                .isEqualTo(membershipBefore.getFirst().tenantId());
    }

    private PlatformRoleAssignment activeAssignment(
            UUID id,
            UUID identityId,
            PlatformRole role) {
        return new PlatformRoleAssignment(
                id,
                identityId,
                role,
                PlatformRoleStatus.ACTIVE,
                Instant.parse("2026-01-01T10:00:00Z"),
                null);
    }
}
