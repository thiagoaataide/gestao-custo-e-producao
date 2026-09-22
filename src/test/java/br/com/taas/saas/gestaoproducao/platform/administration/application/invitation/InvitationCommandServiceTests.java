package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

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
import org.springframework.context.ApplicationEventPublisher;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.config.InvitationLinkProperties;
import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.InvitationConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

class InvitationCommandServiceTests {

    private static final UUID OWNER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ADMIN_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT_USER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void createsAValidInvitationWithTwentyFourHourExpiryAndCopyableLink() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = new InMemoryTenantRepository();
        tenants.save(new Tenant(TENANT_ID, "Active tenant", TenantStatus.ACTIVE, NOW));
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(invitations, tenants, audits, () -> "token-one");

        InvitationLinkResult result = service.createInvitation(new CreateInvitationCommand(
                ADMIN_IDENTITY_ID,
                TENANT_ID,
                " User@Example.COM ",
                NOW));

        assertThat(result.invitation().status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(result.invitation().role()).isEqualTo(MembershipRole.TENANT_USER);
        assertThat(result.invitation().email().value()).isEqualTo("user@example.com");
        assertThat(result.invitation().expiresAt()).isEqualTo(NOW.plusSeconds(24 * 60 * 60));
        assertThat(result.link()).isEqualTo("https://app.example/invitations/token-one");
        assertThat(result.invitation().tokenDigest().value()).doesNotContain("token-one");
        assertThat(audits.events).singleElement()
                .extracting(AuditEvent::action, AuditEvent::result)
                .containsExactly(AuditAction.INVITATION_CREATED, AuditResult.SUCCESS);
    }

    @Test
    void publishesDeliveryRequestAfterCreatingInvitationLink() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = activeTenants();
        var audits = new InMemoryAdministrativeAuditRepository();
        var events = new ArrayList<Object>();
        var service = service(
                invitations,
                tenants,
                audits,
                () -> "token-one",
                events::add);

        InvitationLinkResult result = service.createInvitation(new CreateInvitationCommand(
                ADMIN_IDENTITY_ID,
                TENANT_ID,
                "user@example.com",
                NOW));

        assertThat(events).singleElement()
                .isInstanceOfSatisfying(InvitationDeliveryRequested.class, event -> {
                    assertThat(event.request().invitationId()).isEqualTo(result.invitation().id());
                    assertThat(event.request().actorIdentityId()).isEqualTo(ADMIN_IDENTITY_ID);
                    assertThat(event.request().recipientEmail()).isEqualTo("user@example.com");
                    assertThat(event.request().link()).isEqualTo(result.link());
                });
    }

    @Test
    void rejectsDuplicatePendingInvitationOutsideExplicitResend() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = activeTenants();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(invitations, tenants, audits, () -> "token-one");

        service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, TENANT_ID, "user@example.com", NOW));

        assertThatThrownBy(() -> service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID,
                TENANT_ID,
                " USER@EXAMPLE.COM ",
                NOW.plusSeconds(1))))
                .isInstanceOf(InvitationConflictException.class);
        assertThat(invitations.values).hasSize(1);
        assertThat(audits.events.getLast().result()).isEqualTo(AuditResult.FAILED);
    }

    @Test
    void resendRevokesPreviousInvitationAndCreatesANewLink() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = activeTenants();
        var audits = new InMemoryAdministrativeAuditRepository();
        var tokens = new ArrayList<>(List.of("old-token", "new-token"));
        var service = service(invitations, tenants, audits, tokens::removeFirst);

        InvitationLinkResult previous = service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, TENANT_ID, "user@example.com", NOW));
        InvitationLinkResult replacement = service.resendInvitation(new ResendInvitationCommand(
                ADMIN_IDENTITY_ID, previous.invitation().id(), NOW.plusSeconds(1)));

        assertThat(invitations.findById(previous.invitation().id()).orElseThrow().status())
                .isEqualTo(InvitationStatus.REVOKED);
        assertThat(replacement.invitation().status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(replacement.invitation().id()).isNotEqualTo(previous.invitation().id());
        assertThat(replacement.link()).isEqualTo("https://app.example/invitations/new-token");
        assertThat(audits.events).extracting(AuditEvent::action)
                .containsExactly(
                        AuditAction.INVITATION_CREATED,
                        AuditAction.INVITATION_REVOKED,
                        AuditAction.INVITATION_CREATED);
    }

    @Test
    void deniesInvitationForSuspendedOrClosedTenant() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = new InMemoryTenantRepository();
        UUID suspendedId = UUID.randomUUID();
        UUID closedId = UUID.randomUUID();
        tenants.save(new Tenant(suspendedId, "Suspended", TenantStatus.SUSPENDED, NOW));
        tenants.save(new Tenant(closedId, "Closed", TenantStatus.CLOSED, NOW));
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(invitations, tenants, audits, () -> "token-one");

        assertThatThrownBy(() -> service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, suspendedId, "suspended@example.com", NOW)))
                .isInstanceOf(InvitationTenantUnavailableException.class);
        assertThatThrownBy(() -> service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, closedId, "closed@example.com", NOW)))
                .isInstanceOf(InvitationTenantUnavailableException.class);
        assertThat(invitations.values).isEmpty();
    }

    @Test
    void expiresInvitationAndAllowsAReplacementAfterExpiry() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = activeTenants();
        var audits = new InMemoryAdministrativeAuditRepository();
        var tokens = new ArrayList<>(List.of("expired-token", "replacement-token"));
        var service = service(invitations, tenants, audits, tokens::removeFirst);

        InvitationLinkResult previous = service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, TENANT_ID, "user@example.com", NOW));
        Invitation expired = service.expireInvitation(new ExpireInvitationCommand(
                OWNER_IDENTITY_ID,
                previous.invitation().id(),
                NOW.plusSeconds(24 * 60 * 60 + 1)));
        InvitationLinkResult replacement = service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID,
                TENANT_ID,
                "user@example.com",
                NOW.plusSeconds(24 * 60 * 60 + 2)));

        assertThat(expired.status()).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(replacement.invitation().status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitations.values).hasSize(2);
        assertThat(audits.events).extracting(AuditEvent::action)
                .containsExactly(
                        AuditAction.INVITATION_CREATED,
                        AuditAction.INVITATION_EXPIRED,
                        AuditAction.INVITATION_CREATED);
    }

    @Test
    void revokesInvitationAndRejectsNonPlatformActor() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = activeTenants();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(invitations, tenants, audits, () -> "token-one");
        InvitationLinkResult created = service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, TENANT_ID, "user@example.com", NOW));

        assertThatThrownBy(() -> service.revokeInvitation(new RevokeInvitationCommand(
                TENANT_USER_IDENTITY_ID,
                created.invitation().id(),
                NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);
        assertThat(invitations.findById(created.invitation().id()).orElseThrow().status())
                .isEqualTo(InvitationStatus.PENDING);

        Invitation revoked = service.revokeInvitation(new RevokeInvitationCommand(
                OWNER_IDENTITY_ID,
                created.invitation().id(),
                NOW.plusSeconds(2)));
        assertThat(revoked.status()).isEqualTo(InvitationStatus.REVOKED);
    }

    @Test
    void auditFailureIsPropagatedAfterInvitationPersistenceAttempt() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = activeTenants();
        var audits = new InMemoryAdministrativeAuditRepository();
        audits.failOnSave = true;
        var service = service(invitations, tenants, audits, () -> "token-one");

        assertThatThrownBy(() -> service.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, TENANT_ID, "user@example.com", NOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit failure");
    }

    private static InMemoryTenantRepository activeTenants() {
        var tenants = new InMemoryTenantRepository();
        tenants.save(new Tenant(TENANT_ID, "Active tenant", TenantStatus.ACTIVE, NOW));
        return tenants;
    }

    private static InvitationCommandService service(
            InMemoryInvitationRepository invitations,
            InMemoryTenantRepository tenants,
            InMemoryAdministrativeAuditRepository audits,
            InvitationTokenGenerator tokenGenerator) {
        return service(invitations, tenants, audits, tokenGenerator, event -> {
        });
    }

    private static InvitationCommandService service(
            InMemoryInvitationRepository invitations,
            InMemoryTenantRepository tenants,
            InMemoryAdministrativeAuditRepository audits,
            InvitationTokenGenerator tokenGenerator,
            ApplicationEventPublisher applicationEventPublisher) {
        return new InvitationCommandService(
                invitations,
                tenants,
                authorizationService(),
                audits,
                tokenGenerator,
                new InvitationLinkProperties("https://app.example"),
                applicationEventPublisher);
    }

    private static PlatformAuthorizationService authorizationService() {
        PlatformRoleRepository roles = new InMemoryPlatformRoleRepository();
        return new PlatformAuthorizationService(roles);
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

    private static final class InMemoryInvitationRepository
            implements InvitationRepository {

        private final Map<UUID, Invitation> values = new HashMap<>();

        @Override
        public Optional<Invitation> findById(UUID invitationId) {
            return Optional.ofNullable(values.get(invitationId));
        }

        @Override
        public Optional<Invitation> findPendingByToken(String token, Instant now) {
            var digest = br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest
                    .fromToken(token);
            return values.values().stream()
                    .filter(invitation -> invitation.tokenDigest().equals(digest))
                    .filter(invitation -> invitation.isPendingAt(now))
                    .findFirst();
        }

        @Override
        public Optional<Invitation> findPendingByTenantAndEmail(
                UUID tenantId,
                String email,
                Instant now) {
            return values.values().stream()
                    .filter(invitation -> invitation.tenantId().equals(tenantId))
                    .filter(invitation -> invitation.email().equals(NormalizedEmail.from(email)))
                    .filter(invitation -> invitation.isPendingAt(now))
                    .findFirst();
        }

        @Override
        public Optional<Invitation> findPendingByTenantAndEmailIncludingExpired(
                UUID tenantId,
                String email) {
            return values.values().stream()
                    .filter(invitation -> invitation.tenantId().equals(tenantId))
                    .filter(invitation -> invitation.email().equals(NormalizedEmail.from(email)))
                    .filter(invitation -> invitation.status() == InvitationStatus.PENDING)
                    .findFirst();
        }

        @Override
        public Invitation save(Invitation invitation) {
            values.put(invitation.id(), invitation);
            return invitation;
        }
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
