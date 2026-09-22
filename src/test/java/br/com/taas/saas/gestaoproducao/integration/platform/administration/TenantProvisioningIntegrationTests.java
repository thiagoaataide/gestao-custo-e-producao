package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

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

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.ChangeTenantStatusCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.CreateTenantCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantLifecycleAction;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantLifecycleValidationException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantProvisioningCommandService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantProvisioningQueryService;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@SpringBootTest
@ActiveProfiles("test")
@Import(TenantProvisioningIntegrationTests.FailingAuditConfiguration.class)
class TenantProvisioningIntegrationTests {

    private static final UUID OWNER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ADMIN_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT_USER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final Instant NOW = Instant.parse("2026-09-22T13:00:00Z");

    @Autowired
    private TenantProvisioningCommandService commandService;

    @Autowired
    private TenantProvisioningQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailingAuditRepository auditRepository;

    @BeforeEach
    void resetAuditFailure() {
        auditRepository.fail = false;
    }

    @Test
    void createsTenantWithoutMembershipAndReturnsOnlyAdministrativeMetadata() {
        Tenant tenant = commandService.createTenant(new CreateTenantCommand(
                ADMIN_IDENTITY_ID,
                "Integration tenant " + UUID.randomUUID(),
                NOW));

        Tenant queried = queryService.findTenant(ADMIN_IDENTITY_ID, tenant.id());
        Long memberships = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership WHERE tenant_id = ?",
                Long.class,
                tenant.id());

        assertThat(queried.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(queried.name()).isEqualTo(tenant.name());
        assertThat(memberships).isZero();
    }

    @Test
    void adminCanCreateButOnlyOwnerCanChangeTenantLifecycle() {
        Tenant tenant = commandService.createTenant(new CreateTenantCommand(
                ADMIN_IDENTITY_ID,
                "Admin-created tenant " + UUID.randomUUID(),
                NOW));

        assertThatThrownBy(() -> commandService.changeTenantStatus(new ChangeTenantStatusCommand(
                ADMIN_IDENTITY_ID,
                tenant.id(),
                TenantLifecycleAction.SUSPEND,
                NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);

        Tenant suspended = commandService.changeTenantStatus(new ChangeTenantStatusCommand(
                OWNER_IDENTITY_ID,
                tenant.id(),
                TenantLifecycleAction.SUSPEND,
                NOW.plusSeconds(2)));
        assertThat(suspended.status()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void ownerCanReactivateAndCloseTenantAndClosedStateIsTerminal() {
        Tenant tenant = commandService.createTenant(new CreateTenantCommand(
                OWNER_IDENTITY_ID,
                "Terminal tenant " + UUID.randomUUID(),
                NOW));
        UUID membershipId = UUID.randomUUID();
        UUID membershipIdentityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.external_identity "
                        + "(id, provider, external_subject, status) "
                        + "VALUES (?, 'SUPABASE', ?, 'ACTIVE')",
                membershipIdentityId,
                "t9-preserve-" + membershipIdentityId);
        jdbcTemplate.update(
                "INSERT INTO platform.membership "
                        + "(id, identity_id, tenant_id, status, role) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'TENANT_USER')",
                membershipId,
                membershipIdentityId,
                tenant.id());
        commandService.changeTenantStatus(new ChangeTenantStatusCommand(
                OWNER_IDENTITY_ID,
                tenant.id(),
                TenantLifecycleAction.SUSPEND,
                NOW.plusSeconds(1)));
        commandService.changeTenantStatus(new ChangeTenantStatusCommand(
                OWNER_IDENTITY_ID,
                tenant.id(),
                TenantLifecycleAction.REACTIVATE,
                NOW.plusSeconds(2)));
        Tenant closed = commandService.changeTenantStatus(new ChangeTenantStatusCommand(
                OWNER_IDENTITY_ID,
                tenant.id(),
                TenantLifecycleAction.CLOSE,
                NOW.plusSeconds(3)));

        assertThat(closed.status()).isEqualTo(TenantStatus.CLOSED);
        assertThatThrownBy(() -> commandService.changeTenantStatus(new ChangeTenantStatusCommand(
                OWNER_IDENTITY_ID,
                tenant.id(),
                TenantLifecycleAction.REACTIVATE,
                NOW.plusSeconds(4))))
                .isInstanceOf(TenantLifecycleValidationException.class);
        assertThat(queryService.findTenant(OWNER_IDENTITY_ID, tenant.id()).status())
                .isEqualTo(TenantStatus.CLOSED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership WHERE id = ?",
                Long.class,
                membershipId)).isEqualTo(1L);
    }

    @Test
    void tenantUserCannotCreateTenant() {
        assertThatThrownBy(() -> commandService.createTenant(new CreateTenantCommand(
                TENANT_USER_IDENTITY_ID,
                "Unauthorized tenant " + UUID.randomUUID(),
                NOW)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void auditFailureRollsBackTenantCreation() {
        String tenantName = "Rollback tenant " + UUID.randomUUID();
        auditRepository.fail = true;

        assertThatThrownBy(() -> commandService.createTenant(new CreateTenantCommand(
                OWNER_IDENTITY_ID,
                tenantName,
                NOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated audit failure");

        Long persisted = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.tenant WHERE name = ?",
                Long.class,
                tenantName);
        assertThat(persisted).isZero();
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

        @Bean
        @Primary
        PlatformAuthorizationService tenantTestPlatformAuthorizationService() {
            PlatformRoleRepository roles = new PlatformRoleRepository() {
                private final java.util.List<PlatformRoleAssignment> assignments = java.util.List.of(
                        assignment(OWNER_IDENTITY_ID, PlatformRole.PLATFORM_OWNER),
                        assignment(ADMIN_IDENTITY_ID, PlatformRole.PLATFORM_ADMIN));

                @Override
                public java.util.List<PlatformRoleAssignment> findActiveByIdentityId(
                        UUID identityId) {
                    return assignments.stream()
                            .filter(assignment -> assignment.identityId().equals(identityId))
                            .toList();
                }

                @Override
                public java.util.Optional<PlatformRoleAssignment> findActiveOwner() {
                    return assignments.stream()
                            .filter(assignment -> assignment.role().isOwner())
                            .findFirst();
                }

                @Override
                public PlatformRoleAssignment save(PlatformRoleAssignment assignment) {
                    return assignment;
                }
            };
            return new PlatformAuthorizationService(roles);
        }

        private static PlatformRoleAssignment assignment(UUID identityId, PlatformRole role) {
            return new PlatformRoleAssignment(
                    UUID.randomUUID(),
                    identityId,
                    role,
                    PlatformRoleStatus.ACTIVE,
                    NOW,
                    null);
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
