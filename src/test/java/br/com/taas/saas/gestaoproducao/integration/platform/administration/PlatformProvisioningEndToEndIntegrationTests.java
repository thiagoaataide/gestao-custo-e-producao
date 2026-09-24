package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.application.port.out.AuthenticatedIdentityPort;
import br.com.taas.saas.gestaoproducao.platform.administration.application.BootstrapOwnerService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.AcceptInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceResult;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.CreateInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationCommandService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationLinkResult;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdminService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.GrantPlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.RevokePlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.ChangeTenantStatusCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.CreateTenantCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantLifecycleAction;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantProvisioningCommandService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.audit.AdministrativeAuditQueryService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

@SpringBootTest
@ActiveProfiles("test")
@Import(PlatformProvisioningEndToEndIntegrationTests.TestSupportConfiguration.class)
class PlatformProvisioningEndToEndIntegrationTests {

    private static final String PUBLIC_INVITATION_ORIGIN =
            "https://gestao-custo-e-producao.onrender.com";
    private static final UUID OWNER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final ExternalSubject OWNER_SUBJECT =
            ExternalSubject.fromSupabase("test-subject-a");
    private static final Instant NOW = Instant.parse("2026-09-22T16:00:00Z");

    @Autowired
    private BootstrapOwnerService bootstrapOwnerService;

    @Autowired
    private TenantProvisioningCommandService tenantCommandService;

    @Autowired
    private InvitationCommandService invitationCommandService;

    @Autowired
    private InvitationAcceptanceService invitationAcceptanceService;

    @Autowired
    private PlatformMembershipAdminService membershipAdminService;

    @Autowired
    private AdministrativeAuditQueryService auditQueryService;

    @Autowired
    private AccessDecisionResolver accessDecisionResolver;

    @Autowired
    private TenantScopedTransactionExecutor transactionExecutor;

    @Autowired
    private PlatformRoleRepository platformRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubAuthenticatedIdentityPort authenticatedIdentityPort;

    private boolean ownerCreatedByTest;

    @DynamicPropertySource
    static void configurePublicInvitationOrigin(DynamicPropertyRegistry registry) {
        registry.add("platform.invitation.base-url", () -> PUBLIC_INVITATION_ORIGIN);
    }

    @BeforeEach
    void ensureOwnerAndResetIdentityStub() {
        authenticatedIdentityPort.profile = Optional.empty();
        ownerCreatedByTest = platformRoleRepository.findActiveOwner().isEmpty();
        PlatformRoleAssignment owner = bootstrapOwnerService.bootstrapOwner(OWNER_SUBJECT, NOW);
        assertThat(owner.identityId()).isEqualTo(OWNER_IDENTITY_ID);
        assertThat(owner.role()).isEqualTo(PlatformRole.PLATFORM_OWNER);
    }

    @AfterEach
    void revokeOwnerCreatedByThisTest() {
        if (ownerCreatedByTest) {
            jdbcTemplate.update(
                    "UPDATE platform.platform_role_assignment "
                            + "SET status = 'REVOKED', revoked_at = ? "
                            + "WHERE role = 'PLATFORM_OWNER' AND status = 'ACTIVE'",
                    Timestamp.from(NOW.plusSeconds(1)));
        }
        authenticatedIdentityPort.profile = Optional.empty();
    }

    @Test
    void bootstrapIsIdempotentAndOrdinaryIdentityIsNotProvisionedAutomatically() {
        PlatformRoleAssignment first = bootstrapOwnerService.bootstrapOwner(OWNER_SUBJECT, NOW);
        PlatformRoleAssignment repeated = bootstrapOwnerService.bootstrapOwner(
                OWNER_SUBJECT,
                NOW.plusSeconds(1));

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(platformRoleRepository.findActiveOwner())
                .map(PlatformRoleAssignment::id)
                .contains(first.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.platform_role_assignment "
                        + "WHERE role = 'PLATFORM_OWNER' AND status = 'ACTIVE'",
                Long.class)).isEqualTo(1L);
        assertThat(accessDecisionResolver.resolve(OWNER_SUBJECT).type())
                .isEqualTo(AccessDecisionType.PLATFORM_ACCESS);
        assertThat(accessDecisionResolver.resolve(
                ExternalSubject.fromSupabase(unique("ordinary-subject"))).type())
                .isEqualTo(AccessDecisionType.NOT_PROVISIONED);
    }

    @Test
    void ownerCreatesTenantWithoutMembershipAndLifecycleControlsOperationalAccess() {
        Tenant tenant = createTenant("lifecycle");
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership WHERE tenant_id = ?",
                Long.class,
                tenant.id())).isZero();

        String subject = unique("lifecycle-user");
        UUID identityId = insertIdentity(subject);
        insertActiveMembership(identityId, tenant.id());
        ExternalSubject externalSubject = ExternalSubject.fromSupabase(subject);
        assertThat(accessDecisionResolver.resolve(externalSubject).type())
                .isEqualTo(AccessDecisionType.TENANT_ACCESS);

        Tenant suspended = changeTenant(tenant, TenantLifecycleAction.SUSPEND);
        assertThat(suspended.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(accessDecisionResolver.resolve(externalSubject).type())
                .isEqualTo(AccessDecisionType.NOT_PROVISIONED);
        assertThatThrownBy(() -> transactionExecutor.execute(externalSubject, () -> "must not run"))
                .isInstanceOf(TenantAccessDeniedException.class);

        Tenant reactivated = changeTenant(tenant, TenantLifecycleAction.REACTIVATE);
        assertThat(reactivated.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(accessDecisionResolver.resolve(externalSubject).type())
                .isEqualTo(AccessDecisionType.TENANT_ACCESS);

        Tenant closed = changeTenant(tenant, TenantLifecycleAction.CLOSE);
        assertThat(closed.status()).isEqualTo(TenantStatus.CLOSED);
        assertThatThrownBy(() -> changeTenant(tenant, TenantLifecycleAction.REACTIVATE))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.tenant WHERE id = ?",
                Long.class,
                tenant.id())).isEqualTo(1L);
    }

    @Test
    void publicOriginInvitationFlowCreatesLinkAndActivatesMembershipWithAudit() {
        Tenant tenant = createTenant("invitation");
        String subject = unique("invited-subject");
        String email = unique("invited") + "@example.com";
        authenticatedIdentityPort.verified(subject, email);

        InvitationLinkResult link = invitationCommandService.createInvitation(
                new CreateInvitationCommand(OWNER_IDENTITY_ID, tenant.id(), email, NOW));
        assertThat(link.invitation().status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(link.invitation().expiresAt()).isEqualTo(NOW.plusSeconds(24 * 60 * 60));
        assertThat(link.link()).startsWith(PUBLIC_INVITATION_ORIGIN + "/invitations/");
        assertThat(link.link()).isEqualTo(PUBLIC_INVITATION_ORIGIN + "/invitations/" + tokenFrom(link));

        InvitationAcceptanceResult accepted = invitationAcceptanceService.acceptInvitation(
                new AcceptInvitationCommand(
                        tokenFrom(link),
                        new AccessTokenContext(ExternalSubject.fromSupabase(subject), "validated-token"),
                        NOW.plusSeconds(60)));

        assertThat(accepted.invitation().status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(accepted.membership().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(accepted.membership().tenantId()).isEqualTo(tenant.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership "
                        + "WHERE identity_id = ? AND tenant_id = ? AND status = 'ACTIVE'",
                Long.class,
                accepted.membership().identityId(),
                tenant.id())).isEqualTo(1L);
        assertThat(accessDecisionResolver.resolve(ExternalSubject.fromSupabase(subject)).type())
                .isEqualTo(AccessDecisionType.TENANT_ACCESS);
        assertThat(transactionExecutor.execute(
                ExternalSubject.fromSupabase(subject),
                () -> jdbcTemplate.queryForObject(
                        "SELECT current_setting('app.tenant_id', true)",
                        String.class)))
                .isEqualTo(tenant.id().toString());

        AuditEventPage acceptedInvitationAudit = auditQueryService.findPage(
                OWNER_IDENTITY_ID,
                new AuditEventQuery(
                        accepted.membership().identityId(),
                        AuditAction.INVITATION_ACCEPTED,
                        AuditTargetType.INVITATION,
                        link.invitation().id(),
                        AuditResult.SUCCESS,
                        null,
                        null,
                        0,
                        20));
        assertThat(acceptedInvitationAudit.content()).singleElement().satisfies(event -> {
            assertThat(event.actorIdentityId()).isEqualTo(accepted.membership().identityId());
            assertThat(event.metadata().values()).isEmpty();
        });

        Tenant secondTenant = createTenant("second-invitation");
        InvitationLinkResult secondLink = invitationCommandService.createInvitation(
                new CreateInvitationCommand(OWNER_IDENTITY_ID, secondTenant.id(), email, NOW));
        assertThatThrownBy(() -> invitationAcceptanceService.acceptInvitation(
                new AcceptInvitationCommand(
                        tokenFrom(secondLink),
                        new AccessTokenContext(ExternalSubject.fromSupabase(subject), "validated-token"),
                        NOW.plusSeconds(120))))
                .isInstanceOf(InvitationAcceptanceException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                secondLink.invitation().id())).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership WHERE identity_id = ? AND status = 'ACTIVE'",
                Long.class,
                accepted.membership().identityId())).isEqualTo(1L);
    }

    @Test
    void platformAdminIsSeparateAndCannotManagePlatformRoles() {
        UUID adminIdentityId = insertIdentity(unique("platform-admin"));
        PlatformRoleAssignment admin = membershipAdminService.grantPlatformAdmin(
                new GrantPlatformAdminCommand(OWNER_IDENTITY_ID, adminIdentityId, NOW));

        assertThat(admin.role()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(accessDecisionResolver.resolve(
                ExternalSubject.fromSupabase(findSubject(adminIdentityId))).type())
                .isEqualTo(AccessDecisionType.PLATFORM_ACCESS);

        UUID targetIdentityId = insertIdentity(unique("role-target"));
        assertThatThrownBy(() -> membershipAdminService.grantPlatformAdmin(
                new GrantPlatformAdminCommand(adminIdentityId, targetIdentityId, NOW)))
                .isInstanceOf(RuntimeException.class);
        assertThat(platformRoleRepository.findActiveByIdentityId(targetIdentityId)).isEmpty();

        PlatformRoleAssignment revoked = membershipAdminService.revokePlatformAdmin(
                new RevokePlatformAdminCommand(OWNER_IDENTITY_ID, admin.id(), NOW.plusSeconds(1)));
        assertThat(revoked.status()).isEqualTo(PlatformRoleStatus.REVOKED);
        assertThat(accessDecisionResolver.resolve(
                ExternalSubject.fromSupabase(findSubject(adminIdentityId))).type())
                .isEqualTo(AccessDecisionType.NOT_PROVISIONED);
    }

    @Test
    void deniedMutationIsAuditedAndAuditQueryReturnsOnlyPlatformMetadata() {
        Tenant tenant = createTenant("audit");
        String subject = unique("audit-user");
        String email = unique("audit") + "@example.com";
        authenticatedIdentityPort.verified(subject, email);
        InvitationLinkResult invitation = invitationCommandService.createInvitation(
                new CreateInvitationCommand(OWNER_IDENTITY_ID, tenant.id(), email, NOW));
        InvitationAcceptanceResult accepted = invitationAcceptanceService.acceptInvitation(
                new AcceptInvitationCommand(
                        tokenFrom(invitation),
                        new AccessTokenContext(ExternalSubject.fromSupabase(subject), "validated-token"),
                        NOW.plusSeconds(30)));

        assertThatThrownBy(() -> tenantCommandService.changeTenantStatus(
                new ChangeTenantStatusCommand(
                        accepted.membership().identityId(),
                        tenant.id(),
                        TenantLifecycleAction.SUSPEND,
                        NOW.plusSeconds(60))))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.tenant WHERE id = ?",
                String.class,
                tenant.id())).isEqualTo("ACTIVE");

        AuditEventPage denied = auditQueryService.findPage(
                OWNER_IDENTITY_ID,
                new AuditEventQuery(
                        accepted.membership().identityId(),
                        AuditAction.TENANT_SUSPENDED,
                        AuditTargetType.TENANT,
                        tenant.id(),
                        AuditResult.DENIED,
                        null,
                        null,
                        0,
                        20));
        assertThat(denied.content()).hasSize(1);

        AuditEventPage administrativeEvents = auditQueryService.findPage(
                OWNER_IDENTITY_ID,
                AuditEventQuery.firstPage(100));
        assertThat(administrativeEvents.totalElements()).isPositive();
        assertThat(administrativeEvents.content()).allSatisfy(this::assertAdministrativeEvent);
    }

    private void assertAdministrativeEvent(AuditEvent event) {
        assertThat(event.action()).isNotNull();
        assertThat(event.targetType()).isIn(
                AuditTargetType.TENANT,
                AuditTargetType.INVITATION,
                AuditTargetType.MEMBERSHIP,
                AuditTargetType.PLATFORM_ROLE);
        assertThat(event.result()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.metadata().values().keySet())
                .noneMatch(key -> key.contains("token")
                        || key.contains("secret")
                        || key.contains("operation")
                        || key.contains("production")
                        || key.contains("inventory"));
    }

    private Tenant createTenant(String label) {
        return tenantCommandService.createTenant(new CreateTenantCommand(
                OWNER_IDENTITY_ID,
                "T16 " + label + " " + UUID.randomUUID(),
                NOW));
    }

    private Tenant changeTenant(Tenant tenant, TenantLifecycleAction action) {
        return tenantCommandService.changeTenantStatus(new ChangeTenantStatusCommand(
                OWNER_IDENTITY_ID,
                tenant.id(),
                action,
                NOW));
    }

    private UUID insertIdentity(String subject) {
        UUID identityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.external_identity "
                        + "(id, provider, external_subject, status, created_at) "
                        + "VALUES (?, 'SUPABASE', ?, 'ACTIVE', ?)",
                identityId,
                subject,
                Timestamp.from(NOW));
        return identityId;
    }

    private void insertActiveMembership(UUID identityId, UUID tenantId) {
        jdbcTemplate.update(
                "INSERT INTO platform.membership "
                        + "(id, identity_id, tenant_id, status, role, created_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'TENANT_USER', ?)",
                UUID.randomUUID(),
                identityId,
                tenantId,
                Timestamp.from(NOW));
    }

    private String findSubject(UUID identityId) {
        return jdbcTemplate.queryForObject(
                "SELECT external_subject FROM platform.external_identity WHERE id = ?",
                String.class,
                identityId);
    }

    private String tokenFrom(InvitationLinkResult result) {
        return result.link().substring(result.link().lastIndexOf('/') + 1);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfiguration {

        @Bean
        @Primary
        StubAuthenticatedIdentityPort platformProvisioningIdentityPort() {
            return new StubAuthenticatedIdentityPort();
        }
    }

    static final class StubAuthenticatedIdentityPort implements AuthenticatedIdentityPort {

        private volatile Optional<AuthenticatedIdentityProfile> profile = Optional.empty();

        void verified(String subject, String email) {
            profile = Optional.of(new AuthenticatedIdentityProfile(
                    ExternalSubject.fromSupabase(subject),
                    br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail.from(email),
                    true));
        }

        @Override
        public Optional<AuthenticatedIdentityProfile> loadVerifiedProfile(
                AccessTokenContext accessTokenContext) {
            return profile;
        }
    }
}
