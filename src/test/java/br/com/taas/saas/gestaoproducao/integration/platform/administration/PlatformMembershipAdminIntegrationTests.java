package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.MembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdminService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.RevokeMembershipCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.GrantPlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.RevokePlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

@SpringBootTest
@ActiveProfiles("test")
@Import(PlatformMembershipAdminIntegrationTests.FailingAuditConfiguration.class)
class PlatformMembershipAdminIntegrationTests {

    private static final UUID OWNER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-22T13:00:00Z");

    @Autowired
    private PlatformMembershipAdminService service;

    @Autowired
    private PlatformRoleRepository platformRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailingAuditRepository auditRepository;

    private final Set<UUID> createdRoleIds = new HashSet<>();
    private final Set<UUID> createdMembershipIds = new HashSet<>();

    @BeforeEach
    void ensureOwner() {
        auditRepository.fail = false;
        platformRoleRepository.findActiveOwner().ifPresentOrElse(
                owner -> createdRoleIds.add(owner.id()),
                () -> {
                    PlatformRoleAssignment owner = platformRoleRepository.save(
                            new PlatformRoleAssignment(
                    UUID.randomUUID(),
                    OWNER_IDENTITY_ID,
                    PlatformRole.PLATFORM_OWNER,
                    PlatformRoleStatus.ACTIVE,
                    NOW,
                    null));
                    createdRoleIds.add(owner.id());
                });
    }

    @AfterEach
    void cleanUpCreatedState() {
        auditRepository.fail = false;
        for (UUID membershipId : createdMembershipIds) {
            jdbcTemplate.update(
                    "UPDATE platform.membership "
                            + "SET status = 'REVOKED', revoked_at = ? "
                            + "WHERE id = ? AND status <> 'REVOKED'",
                    Timestamp.from(NOW.plusSeconds(1)),
                    membershipId);
        }
        for (UUID roleId : createdRoleIds) {
            jdbcTemplate.update(
                    "UPDATE platform.platform_role_assignment "
                            + "SET status = 'REVOKED', revoked_at = ? "
                            + "WHERE id = ? AND status <> 'REVOKED'",
                    Timestamp.from(NOW.plusSeconds(1)),
                    roleId);
        }
        createdMembershipIds.clear();
        createdRoleIds.clear();
    }

    @Test
    void ownerCanGrantAndRevokeAdminAndRoleHistoryIsPreserved() {
        UUID targetIdentityId = createIdentity();

        PlatformRoleAssignment granted = service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(OWNER_IDENTITY_ID, targetIdentityId, NOW));
        PlatformRoleAssignment revoked = service.revokePlatformAdmin(
                new RevokePlatformAdminCommand(
                        OWNER_IDENTITY_ID,
                        granted.id(),
                        NOW.plusSeconds(1)));

        assertThat(revoked.status()).isEqualTo(PlatformRoleStatus.REVOKED);
        assertThat(platformRoleRepository.findActiveByIdentityId(targetIdentityId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.platform_role_assignment WHERE id = ?",
                String.class,
                granted.id())).isEqualTo("REVOKED");
    }

    @Test
    void commonAdminCannotGrantOrRevokeAnotherPlatformAdmin() {
        UUID adminIdentityId = createIdentity();
        PlatformRoleAssignment admin = saveActiveRole(
                adminIdentityId,
                PlatformRole.PLATFORM_ADMIN);
        UUID targetIdentityId = createIdentity();

        assertThatThrownBy(() -> service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(adminIdentityId, targetIdentityId, NOW)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.revokePlatformAdmin(
                new RevokePlatformAdminCommand(
                        adminIdentityId,
                        admin.id(),
                        NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);

        assertThat(platformRoleRepository.findActiveByIdentityId(adminIdentityId))
                .singleElement()
                .extracting(PlatformRoleAssignment::role)
                .isEqualTo(PlatformRole.PLATFORM_ADMIN);
    }

    @Test
    void ownerCannotCreateAnotherOwnerOrRevokeOwnerAssignment() {
        PlatformRoleAssignment owner = platformRoleRepository.findActiveOwner().orElseThrow();

        assertThatThrownBy(() -> service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(OWNER_IDENTITY_ID, OWNER_IDENTITY_ID, NOW)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.revokePlatformAdmin(
                new RevokePlatformAdminCommand(
                        OWNER_IDENTITY_ID,
                        owner.id(),
                        NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);

        assertThat(platformRoleRepository.findActiveOwner()).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.platform_role_assignment "
                        + "WHERE role = 'PLATFORM_OWNER' AND status = 'ACTIVE'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void platformCanRevokeMembershipWithoutDeletingItsHistory() {
        UUID identityId = createIdentity();
        UUID membershipId = createMembership(identityId);

        Membership revoked = service.revokeMembership(new RevokeMembershipCommand(
                OWNER_IDENTITY_ID,
                membershipId,
                NOW));

        assertThat(revoked.status()).isEqualTo(MembershipStatus.REVOKED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership WHERE id = ?",
                Long.class,
                membershipId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.membership WHERE id = ?",
                String.class,
                membershipId)).isEqualTo("REVOKED");
    }

    @Test
    void queryReturnsOnlyPlatformAndMembershipMetadata() {
        UUID identityId = createIdentity();
        UUID membershipId = createMembership(identityId);
        PlatformRoleAssignment role = saveActiveRole(identityId, PlatformRole.PLATFORM_ADMIN);

        PlatformMembershipAdministrationView view = service.view(OWNER_IDENTITY_ID);

        assertThat(view.platformRoles()).extracting(viewRole -> viewRole.id())
                .contains(role.id());
        assertThat(view.memberships()).extracting(MembershipAdministrationView::id)
                .contains(membershipId);
        assertThat(view.memberships()).allSatisfy(metadata -> {
            assertThat(metadata.id()).isNotNull();
            assertThat(metadata.identityId()).isNotNull();
            assertThat(metadata.tenantId()).isNotNull();
            assertThat(metadata.status()).isNotNull();
            assertThat(metadata.role()).isEqualTo(MembershipRole.TENANT_USER);
            assertThat(metadata.createdAt()).isNotNull();
        });
    }

    @Test
    void auditFailureRollsBackEachAdministrativeMutation() {
        UUID grantTargetIdentityId = createIdentity();
        auditRepository.fail = true;

        assertThatThrownBy(() -> service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(
                        OWNER_IDENTITY_ID,
                        grantTargetIdentityId,
                        NOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated audit failure");
        assertThat(platformRoleRepository.findActiveByIdentityId(grantTargetIdentityId)).isEmpty();

        auditRepository.fail = false;
        PlatformRoleAssignment admin = service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(
                        OWNER_IDENTITY_ID,
                        createIdentity(),
                        NOW));
        auditRepository.fail = true;

        assertThatThrownBy(() -> service.revokePlatformAdmin(
                new RevokePlatformAdminCommand(
                        OWNER_IDENTITY_ID,
                        admin.id(),
                        NOW.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(platformRoleRepository.findById(admin.id()).orElseThrow().status())
                .isEqualTo(PlatformRoleStatus.ACTIVE);

        auditRepository.fail = false;
        UUID membershipIdentityId = createIdentity();
        UUID membershipId = createMembership(membershipIdentityId);
        auditRepository.fail = true;

        assertThatThrownBy(() -> service.revokeMembership(new RevokeMembershipCommand(
                OWNER_IDENTITY_ID,
                membershipId,
                NOW.plusSeconds(2))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.membership WHERE id = ?",
                String.class,
                membershipId)).isEqualTo("ACTIVE");
    }

    private UUID createIdentity() {
        UUID identityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.external_identity "
                        + "(id, provider, external_subject, status) "
                        + "VALUES (?, 'SUPABASE', ?, 'ACTIVE')",
                identityId,
                "t12-admin-" + identityId);
        return identityId;
    }

    private PlatformRoleAssignment saveActiveRole(UUID identityId, PlatformRole role) {
        PlatformRoleAssignment assignment = platformRoleRepository.save(new PlatformRoleAssignment(
                UUID.randomUUID(),
                identityId,
                role,
                PlatformRoleStatus.ACTIVE,
                NOW,
                null));
        createdRoleIds.add(assignment.id());
        return assignment;
    }

    private UUID createMembership(UUID identityId) {
        UUID membershipId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.membership "
                        + "(id, identity_id, tenant_id, status, role, created_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'TENANT_USER', ?)",
                membershipId,
                identityId,
                TENANT_ID,
                Timestamp.from(NOW));
        createdMembershipIds.add(membershipId);
        return membershipId;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration {

        @Bean
        @Primary
        FailingAuditRepository failingAuditRepository(
                @Qualifier("jpaAdministrativeAuditRepository")
                AdministrativeAuditRepository delegate) {
            return new FailingAuditRepository(delegate);
        }
    }

    static final class FailingAuditRepository implements AdministrativeAuditRepository {

        private final AdministrativeAuditRepository delegate;
        private boolean fail;

        FailingAuditRepository(AdministrativeAuditRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public AuditEvent save(AuditEvent event) {
            if (fail) {
                throw new IllegalStateException("simulated audit failure");
            }
            return delegate.save(event);
        }

        @Override
        public AuditEventPage findPage(AuditEventQuery query) {
            return delegate.findPage(query);
        }
    }
}
