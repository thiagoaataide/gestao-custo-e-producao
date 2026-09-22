package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

class TenantProvisioningServiceTests {

    private static final UUID OWNER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ADMIN_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT_USER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void ownerAndAdminCanCreateAnActiveTenantWithoutMembership() {
        var tenants = new InMemoryTenantRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = commandService(tenants, audits);

        Tenant ownerTenant = service.createTenant(command(OWNER_IDENTITY_ID, "Owner tenant"));
        Tenant adminTenant = service.createTenant(command(ADMIN_IDENTITY_ID, "Admin tenant"));

        assertThat(ownerTenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(adminTenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenants.values).hasSize(2);
        assertThat(audits.events)
                .extracting(AuditEvent::action)
                .containsExactly(AuditAction.TENANT_CREATED, AuditAction.TENANT_CREATED);
    }

    @Test
    void adminCannotChangeTenantLifecycle() {
        var tenants = new InMemoryTenantRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = commandService(tenants, audits);
        Tenant tenant = service.createTenant(command(OWNER_IDENTITY_ID, "Protected tenant"));

        assertThatThrownBy(() -> service.changeTenantStatus(new ChangeTenantStatusCommand(
                ADMIN_IDENTITY_ID,
                tenant.id(),
                TenantLifecycleAction.SUSPEND,
                NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);

        assertThat(tenants.findById(tenant.id()).orElseThrow().status())
                .isEqualTo(TenantStatus.ACTIVE);
        assertThat(audits.events).hasSize(2);
        assertThat(audits.events.getLast().result()).isEqualTo(AuditResult.DENIED);
    }

    @Test
    void ownerCanSuspendReactivateAndCloseTenantButClosedIsTerminal() {
        var tenants = new InMemoryTenantRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = commandService(tenants, audits);
        Tenant tenant = service.createTenant(command(OWNER_IDENTITY_ID, "Lifecycle tenant"));

        service.changeTenantStatus(statusCommand(
                OWNER_IDENTITY_ID, tenant.id(), TenantLifecycleAction.SUSPEND, 1));
        service.changeTenantStatus(statusCommand(
                OWNER_IDENTITY_ID, tenant.id(), TenantLifecycleAction.REACTIVATE, 2));
        service.changeTenantStatus(statusCommand(
                OWNER_IDENTITY_ID, tenant.id(), TenantLifecycleAction.CLOSE, 3));

        assertThat(tenants.findById(tenant.id()).orElseThrow().status())
                .isEqualTo(TenantStatus.CLOSED);
        assertThatThrownBy(() -> service.changeTenantStatus(statusCommand(
                OWNER_IDENTITY_ID, tenant.id(), TenantLifecycleAction.REACTIVATE, 4)))
                .isInstanceOf(TenantLifecycleValidationException.class);
        assertThat(audits.events)
                .extracting(AuditEvent::action)
                .containsExactly(
                        AuditAction.TENANT_CREATED,
                        AuditAction.TENANT_SUSPENDED,
                        AuditAction.TENANT_REACTIVATED,
                        AuditAction.TENANT_CLOSED,
                        AuditAction.TENANT_REACTIVATED);
        assertThat(audits.events.getLast().result()).isEqualTo(AuditResult.FAILED);
    }

    @Test
    void tenantUserCannotCreateOrChangeTenant() {
        var tenants = new InMemoryTenantRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = commandService(tenants, audits);

        assertThatThrownBy(() -> service.createTenant(
                command(TENANT_USER_IDENTITY_ID, "Unauthorized tenant")))
                .isInstanceOf(RuntimeException.class);
        assertThat(tenants.values).isEmpty();
        assertThat(audits.events).singleElement()
                .extracting(AuditEvent::result)
                .isEqualTo(AuditResult.DENIED);
    }

    @Test
    void queryRequiresPlatformAccessAndReturnsTenantMetadata() {
        var tenants = new InMemoryTenantRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var commandService = commandService(tenants, audits);
        Tenant tenant = commandService.createTenant(command(OWNER_IDENTITY_ID, "Query tenant"));
        var queryService = new TenantProvisioningQueryService(
                tenants,
                authorizationService());

        assertThat(queryService.findTenant(ADMIN_IDENTITY_ID, tenant.id()))
                .extracting(Tenant::id, Tenant::name, Tenant::status)
                .containsExactly(tenant.id(), "Query tenant", TenantStatus.ACTIVE);
        assertThatThrownBy(() -> queryService.findTenant(TENANT_USER_IDENTITY_ID, tenant.id()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void auditFailureStopsTheMutationBeforeTheCommandCompletes() {
        var tenants = new InMemoryTenantRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        audits.failOnSave = true;
        var service = commandService(tenants, audits);

        assertThatThrownBy(() -> service.createTenant(command(
                OWNER_IDENTITY_ID, "Audit failure tenant")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit failure");
        assertThat(audits.events).isEmpty();
    }

    private static TenantProvisioningCommandService commandService(
            InMemoryTenantRepository tenants,
            InMemoryAdministrativeAuditRepository audits) {
        return new TenantProvisioningCommandService(
                tenants,
                authorizationService(),
                audits);
    }

    private static PlatformAuthorizationService authorizationService() {
        PlatformRoleRepository roles = new InMemoryPlatformRoleRepository();
        return new PlatformAuthorizationService(roles);
    }

    private static CreateTenantCommand command(UUID actorIdentityId, String name) {
        return new CreateTenantCommand(actorIdentityId, name, NOW);
    }

    private static ChangeTenantStatusCommand statusCommand(
            UUID actorIdentityId,
            UUID tenantId,
            TenantLifecycleAction action,
            int seconds) {
        return new ChangeTenantStatusCommand(
                actorIdentityId,
                tenantId,
                action,
                NOW.plusSeconds(seconds));
    }

    private static final class InMemoryPlatformRoleRepository
            implements PlatformRoleRepository {

        private final List<PlatformRoleAssignment> assignments = List.of(
                assignment(OWNER_IDENTITY_ID, PlatformRole.PLATFORM_OWNER),
                assignment(ADMIN_IDENTITY_ID, PlatformRole.PLATFORM_ADMIN));

        @Override
        public List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId) {
            return assignments.stream()
                    .filter(assignment -> assignment.identityId().equals(identityId))
                    .toList();
        }

        @Override
        public Optional<PlatformRoleAssignment> findActiveOwner() {
            return assignments.stream()
                    .filter(assignment -> assignment.role().isOwner())
                    .findFirst();
        }

        @Override
        public PlatformRoleAssignment save(PlatformRoleAssignment assignment) {
            return assignment;
        }
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

    private static final class InMemoryTenantRepository implements TenantRepository {

        private final Map<UUID, Tenant> values = new HashMap<>();

        @Override
        public Optional<Tenant> findById(UUID tenantId) {
            return Optional.ofNullable(values.get(tenantId));
        }

        @Override
        public Tenant save(Tenant tenant) {
            values.put(tenant.id(), tenant);
            return tenant;
        }
    }

    private static final class InMemoryAdministrativeAuditRepository
            implements AdministrativeAuditRepository {

        private final List<AuditEvent> events = new ArrayList<>();
        private boolean failOnSave;

        @Override
        public AuditEvent save(AuditEvent event) {
            if (failOnSave) {
                throw new IllegalStateException("audit failure");
            }
            events.add(event);
            return event;
        }

        @Override
        public AuditEventPage findPage(AuditEventQuery query) {
            return new AuditEventPage(events, query.page(), query.size(), events.size());
        }
    }
}
