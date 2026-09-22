package br.com.taas.saas.gestaoproducao.platform.administration.application.membership;

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
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.GrantPlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.RevokePlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

class PlatformMembershipAdminServiceTests {

    private static final UUID OWNER_IDENTITY_ID = UUID.randomUUID();
    private static final UUID ADMIN_IDENTITY_ID = UUID.randomUUID();
    private static final UUID TENANT_USER_IDENTITY_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void ownerCanGrantAndRevokePlatformAdminWithoutChangingTenantMemberships() {
        var roles = new InMemoryPlatformRoleRepository(
                activeRole(OWNER_IDENTITY_ID, PlatformRole.PLATFORM_OWNER));
        var memberships = new InMemoryMembershipRepository();
        Membership tenantMembership = activeMembership(TENANT_USER_IDENTITY_ID);
        memberships.save(tenantMembership);
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(roles, memberships, audits);

        PlatformRoleAssignment granted = service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(OWNER_IDENTITY_ID, ADMIN_IDENTITY_ID, NOW));
        PlatformRoleAssignment revoked = service.revokePlatformAdmin(
                new RevokePlatformAdminCommand(
                        OWNER_IDENTITY_ID,
                        granted.id(),
                        NOW.plusSeconds(1)));

        assertThat(revoked.status()).isEqualTo(PlatformRoleStatus.REVOKED);
        assertThat(roles.findById(granted.id()).orElseThrow().status())
                .isEqualTo(PlatformRoleStatus.REVOKED);
        assertThat(memberships.findById(tenantMembership.id()).orElseThrow().status())
                .isEqualTo(MembershipStatus.ACTIVE);
        assertThat(audits.events)
                .extracting(AuditEvent::action, AuditEvent::result)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                AuditAction.PLATFORM_ADMIN_GRANTED,
                                AuditResult.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple(
                                AuditAction.PLATFORM_ADMIN_REVOKED,
                                AuditResult.SUCCESS));
    }

    @Test
    void adminCannotGrantOrRevokePlatformAdmin() {
        var roles = new InMemoryPlatformRoleRepository(
                activeRole(OWNER_IDENTITY_ID, PlatformRole.PLATFORM_OWNER),
                activeRole(ADMIN_IDENTITY_ID, PlatformRole.PLATFORM_ADMIN));
        var memberships = new InMemoryMembershipRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(roles, memberships, audits);

        assertThatThrownBy(() -> service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(ADMIN_IDENTITY_ID, TENANT_USER_IDENTITY_ID, NOW)))
                .isInstanceOf(RuntimeException.class);

        PlatformRoleAssignment target = roles.findActiveByIdentityId(ADMIN_IDENTITY_ID)
                .getFirst();
        assertThatThrownBy(() -> service.revokePlatformAdmin(
                new RevokePlatformAdminCommand(
                        ADMIN_IDENTITY_ID,
                        target.id(),
                        NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);

        assertThat(roles.findActiveByIdentityId(ADMIN_IDENTITY_ID)).hasSize(1);
        assertThat(audits.events)
                .extracting(AuditEvent::result)
                .containsExactly(AuditResult.DENIED, AuditResult.DENIED);
    }

    @Test
    void ownerCannotCreateAnotherOwnerOrRevokeOwnOwnerAssignment() {
        PlatformRoleAssignment owner = activeRole(
                OWNER_IDENTITY_ID,
                PlatformRole.PLATFORM_OWNER);
        var roles = new InMemoryPlatformRoleRepository(owner);
        var memberships = new InMemoryMembershipRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(roles, memberships, audits);

        assertThatThrownBy(() -> service.grantPlatformAdmin(
                new GrantPlatformAdminCommand(OWNER_IDENTITY_ID, OWNER_IDENTITY_ID, NOW)))
                .isInstanceOf(PlatformMembershipAdminValidationException.class);
        assertThatThrownBy(() -> service.revokePlatformAdmin(
                new RevokePlatformAdminCommand(
                        OWNER_IDENTITY_ID,
                        owner.id(),
                        NOW.plusSeconds(1))))
                .isInstanceOf(PlatformMembershipAdminValidationException.class);

        assertThat(roles.findActiveOwner()).isPresent();
        assertThat(roles.findActiveByIdentityId(OWNER_IDENTITY_ID)).singleElement()
                .extracting(PlatformRoleAssignment::role)
                .isEqualTo(PlatformRole.PLATFORM_OWNER);
    }

    @Test
    void platformAdminCanRevokeTenantMembershipAndHistoryRemainsQueryable() {
        var roles = new InMemoryPlatformRoleRepository(
                activeRole(ADMIN_IDENTITY_ID, PlatformRole.PLATFORM_ADMIN));
        var memberships = new InMemoryMembershipRepository();
        Membership membership = activeMembership(TENANT_USER_IDENTITY_ID);
        memberships.save(membership);
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(roles, memberships, audits);

        Membership revoked = service.revokeMembership(new RevokeMembershipCommand(
                ADMIN_IDENTITY_ID,
                membership.id(),
                NOW.plusSeconds(1)));

        assertThat(revoked.status()).isEqualTo(MembershipStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(memberships.findById(membership.id())).contains(revoked);
        assertThat(audits.events).singleElement()
                .extracting(AuditEvent::action, AuditEvent::result)
                .containsExactly(AuditAction.MEMBERSHIP_REVOKED, AuditResult.SUCCESS);
    }

    @Test
    void tenantUserCannotRevokeMembershipAndAdministrativeQueryIsProtected() {
        var roles = new InMemoryPlatformRoleRepository(
                activeRole(OWNER_IDENTITY_ID, PlatformRole.PLATFORM_OWNER));
        var memberships = new InMemoryMembershipRepository();
        Membership membership = activeMembership(TENANT_USER_IDENTITY_ID);
        memberships.save(membership);
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(roles, memberships, audits);

        assertThatThrownBy(() -> service.revokeMembership(new RevokeMembershipCommand(
                TENANT_USER_IDENTITY_ID,
                membership.id(),
                NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.view(TENANT_USER_IDENTITY_ID))
                .isInstanceOf(RuntimeException.class);

        PlatformMembershipAdministrationView view = service.view(OWNER_IDENTITY_ID);
        assertThat(view.platformRoles()).singleElement()
                .extracting(PlatformRoleAdministrationView::role)
                .isEqualTo(PlatformRole.PLATFORM_OWNER);
        assertThat(view.memberships()).singleElement()
                .extracting(MembershipAdministrationView::identityId,
                        MembershipAdministrationView::tenantId)
                .containsExactly(TENANT_USER_IDENTITY_ID, TENANT_ID);
        assertThat(view.memberships().getFirst().getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("order")
                        || field.getName().toLowerCase().contains("stock")
                        || field.getName().toLowerCase().contains("production"));
    }

    private static PlatformMembershipAdminService service(
            InMemoryPlatformRoleRepository roles,
            InMemoryMembershipRepository memberships,
            InMemoryAdministrativeAuditRepository audits) {
        return new PlatformMembershipAdminService(
                roles,
                memberships,
                new PlatformAuthorizationService(roles),
                audits);
    }

    private static PlatformRoleAssignment activeRole(UUID identityId, PlatformRole role) {
        return new PlatformRoleAssignment(
                UUID.randomUUID(),
                identityId,
                role,
                PlatformRoleStatus.ACTIVE,
                NOW,
                null);
    }

    private static Membership activeMembership(UUID identityId) {
        return new Membership(
                UUID.randomUUID(),
                identityId,
                TENANT_ID,
                MembershipStatus.ACTIVE,
                MembershipRole.TENANT_USER,
                NOW,
                null);
    }

    private static final class InMemoryPlatformRoleRepository implements PlatformRoleRepository {

        private final Map<UUID, PlatformRoleAssignment> assignments = new HashMap<>();

        private InMemoryPlatformRoleRepository(PlatformRoleAssignment... initial) {
            for (PlatformRoleAssignment assignment : initial) {
                assignments.put(assignment.id(), assignment);
            }
        }

        @Override
        public List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId) {
            return assignments.values().stream()
                    .filter(PlatformRoleAssignment::isActive)
                    .filter(assignment -> assignment.identityId().equals(identityId))
                    .toList();
        }

        @Override
        public Optional<PlatformRoleAssignment> findActiveOwner() {
            return assignments.values().stream()
                    .filter(PlatformRoleAssignment::isActive)
                    .filter(assignment -> assignment.role().isOwner())
                    .findFirst();
        }

        @Override
        public Optional<PlatformRoleAssignment> findById(UUID assignmentId) {
            return Optional.ofNullable(assignments.get(assignmentId));
        }

        @Override
        public List<PlatformRoleAssignment> findAll() {
            return new ArrayList<>(assignments.values());
        }

        @Override
        public PlatformRoleAssignment save(PlatformRoleAssignment assignment) {
            assignments.put(assignment.id(), assignment);
            return assignment;
        }
    }

    private static final class InMemoryMembershipRepository implements MembershipRepository {

        private final Map<UUID, Membership> memberships = new HashMap<>();

        @Override
        public Optional<Membership> findById(UUID membershipId) {
            return Optional.ofNullable(memberships.get(membershipId));
        }

        @Override
        public List<Membership> findAll() {
            return new ArrayList<>(memberships.values());
        }

        @Override
        public List<Membership> findActiveByIdentityId(UUID identityId) {
            return memberships.values().stream()
                    .filter(Membership::isActive)
                    .filter(membership -> membership.identityId().equals(identityId))
                    .toList();
        }

        @Override
        public Membership save(Membership membership) {
            memberships.put(membership.id(), membership);
            return membership;
        }
    }

    private static final class InMemoryAdministrativeAuditRepository
            implements AdministrativeAuditRepository {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public AuditEventPage findPage(AuditEventQuery query) {
            return new AuditEventPage(events, query.page(), query.size(), events.size());
        }
    }
}
