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

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.application.port.out.AuthenticatedIdentityPort;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentityStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

class InvitationAcceptanceServiceTests {

    private static final UUID CREATOR_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID RECIPIENT_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID OTHER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000112");
    private static final UUID TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-22T13:00:00Z");
    private static final AccessTokenContext ACCESS_TOKEN = new AccessTokenContext(
            ExternalSubject.fromSupabase("recipient-subject"),
            "validated-access-token");

    @Test
    void acceptsVerifiedMatchingInvitationAndCreatesActiveTenantUser() {
        var fixture = fixture();
        Invitation invitation = fixture.invitation("invite-token", "user@example.com", null);

        InvitationAcceptanceResult result = fixture.service.acceptInvitation(
                command("invite-token", verifiedProfile("user@example.com")));

        assertThat(result.invitation().status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(result.invitation().identityId()).isEqualTo(result.membership().identityId());
        assertThat(result.membership().tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.membership().role()).isEqualTo(MembershipRole.TENANT_USER);
        assertThat(result.membership().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(fixture.invitations.findById(invitation.id()).orElseThrow().status())
                .isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(fixture.audits.events).extracting(AuditEvent::action, AuditEvent::result)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                AuditAction.MEMBERSHIP_CREATED, AuditResult.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple(
                                AuditAction.INVITATION_ACCEPTED, AuditResult.SUCCESS));
    }

    @Test
    void rejectsUnauthenticatedUnverifiedAndMismatchedProfilesWithoutActivatingMembership() {
        for (var profile : List.of(
                Optional.<AuthenticatedIdentityProfile>empty(),
                Optional.of(unverifiedProfile("user@example.com")),
                Optional.of(verifiedProfile("another@example.com")))) {
            var fixture = fixture();
            fixture.identityPort.profile = profile;
            fixture.invitation("invite-token", "user@example.com", null);

            assertThatThrownBy(() -> fixture.service.acceptInvitation(
                    new AcceptInvitationCommand("invite-token", ACCESS_TOKEN, NOW)))
                    .isInstanceOf(InvitationAcceptanceException.class);
            assertThat(fixture.invitations.values.values()).allMatch(
                    invitation -> invitation.status() == InvitationStatus.PENDING);
            assertThat(fixture.memberships.values).isEmpty();
            assertThat(fixture.audits.events).allMatch(
                    event -> event.result() == AuditResult.DENIED);
        }
    }

    @Test
    void rejectsExpiredOrClosedInvitationWithoutChangingItsState() {
        var expiredFixture = fixture();
        Invitation expired = new Invitation(
                UUID.randomUUID(),
                TENANT_ID,
                NormalizedEmail.from("user@example.com"),
                MembershipRole.TENANT_USER,
                InvitationStatus.PENDING,
                InvitationTokenDigest.fromToken("expired-token"),
                null,
                NOW,
                null,
                null,
                CREATOR_IDENTITY_ID,
                NOW.minusSeconds(60));
        expiredFixture.invitations.save(expired);

        assertThatThrownBy(() -> expiredFixture.service.acceptInvitation(
                command("expired-token", verifiedProfile("user@example.com"))))
                .isInstanceOf(InvitationAcceptanceException.class);
        assertThat(expiredFixture.invitations.findById(expired.id()).orElseThrow().status())
                .isEqualTo(InvitationStatus.PENDING);

        var closedFixture = fixture();
        closedFixture.tenants.save(new Tenant(TENANT_ID, "Closed tenant", TenantStatus.CLOSED, NOW));
        closedFixture.invitation("closed-token", "user@example.com", null);

        assertThatThrownBy(() -> closedFixture.service.acceptInvitation(
                command("closed-token", verifiedProfile("user@example.com"))))
                .isInstanceOf(InvitationAcceptanceException.class);
        assertThat(closedFixture.memberships.values).isEmpty();
        assertThat(closedFixture.audits.events).singleElement()
                .extracting(AuditEvent::result)
                .isEqualTo(AuditResult.DENIED);
    }

    @Test
    void blocksIdentityWithAnActiveMembershipInAnotherTenantAndPreservesIt() {
        var fixture = fixture();
        ExternalIdentity identity = identity(RECIPIENT_IDENTITY_ID, "recipient-subject");
        fixture.identities.save(identity);
        Membership current = new Membership(
                UUID.randomUUID(),
                identity.id(),
                OTHER_TENANT_ID,
                MembershipStatus.ACTIVE,
                MembershipRole.TENANT_USER,
                NOW.minusSeconds(60),
                null);
        fixture.memberships.save(current);
        fixture.invitation("invite-token", "user@example.com", null);

        assertThatThrownBy(() -> fixture.service.acceptInvitation(
                command("invite-token", verifiedProfile("user@example.com"))))
                .isInstanceOf(InvitationAcceptanceException.class);
        assertThat(fixture.memberships.findActiveByIdentityId(identity.id()))
                .containsExactly(current);
        assertThat(fixture.invitations.values.values()).singleElement()
                .extracting(Invitation::status)
                .isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void manualIdentityAssociationStillRequiresTheAuthenticatedUserConfirmation() {
        var fixture = fixture();
        ExternalIdentity identity = identity(RECIPIENT_IDENTITY_ID, "recipient-subject");
        fixture.identities.save(identity);
        fixture.invitation("invite-token", "user@example.com", identity.id());
        fixture.identityPort.profile = Optional.empty();

        assertThatThrownBy(() -> fixture.service.acceptInvitation(
                new AcceptInvitationCommand("invite-token", ACCESS_TOKEN, NOW)))
                .isInstanceOf(InvitationAcceptanceException.class);
        assertThat(fixture.memberships.values).isEmpty();
        assertThat(fixture.invitations.values.values()).singleElement()
                .extracting(Invitation::status)
                .isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void repeatedAcceptanceCannotReuseTheInvitation() {
        var fixture = fixture();
        fixture.invitation("invite-token", "user@example.com", null);
        fixture.service.acceptInvitation(command("invite-token", verifiedProfile("user@example.com")));

        assertThatThrownBy(() -> fixture.service.acceptInvitation(
                command("invite-token", verifiedProfile("user@example.com"))))
                .isInstanceOf(InvitationAcceptanceException.class);
        assertThat(fixture.memberships.values).hasSize(1);
        assertThat(fixture.audits.events).extracting(AuditEvent::result)
                .containsExactly(
                        AuditResult.SUCCESS,
                        AuditResult.SUCCESS,
                        AuditResult.DENIED);
    }

    private static AcceptInvitationCommand command(
            String token,
            AuthenticatedIdentityProfile profile) {
        return new AcceptInvitationCommand(token, ACCESS_TOKEN, NOW);
    }

    private static AuthenticatedIdentityProfile verifiedProfile(String email) {
        return new AuthenticatedIdentityProfile(
                ExternalSubject.fromSupabase("recipient-subject"),
                NormalizedEmail.from(email),
                true);
    }

    private static AuthenticatedIdentityProfile unverifiedProfile(String email) {
        return new AuthenticatedIdentityProfile(
                ExternalSubject.fromSupabase("recipient-subject"),
                NormalizedEmail.from(email),
                false);
    }

    private static ExternalIdentity identity(UUID id, String subject) {
        return new ExternalIdentity(
                id,
                ExternalSubject.fromSupabase(subject),
                ExternalIdentityStatus.ACTIVE,
                NOW.minusSeconds(60));
    }

    private static Fixture fixture() {
        var invitations = new InMemoryInvitationRepository();
        var tenants = new InMemoryTenantRepository();
        tenants.save(new Tenant(TENANT_ID, "Active tenant", TenantStatus.ACTIVE, NOW));
        tenants.save(new Tenant(OTHER_TENANT_ID, "Other tenant", TenantStatus.ACTIVE, NOW));
        var identities = new InMemoryExternalIdentityRepository();
        identities.save(identity(CREATOR_IDENTITY_ID, "creator-subject"));
        var memberships = new InMemoryMembershipRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var identityPort = new StubAuthenticatedIdentityPort();
        identityPort.profile = Optional.of(verifiedProfile("user@example.com"));
        var service = new InvitationAcceptanceService(
                identityPort,
                invitations,
                tenants,
                identities,
                memberships,
                audits);
        return new Fixture(
                service,
                invitations,
                tenants,
                identities,
                memberships,
                audits,
                identityPort);
    }

    private record Fixture(
            InvitationAcceptanceService service,
            InMemoryInvitationRepository invitations,
            InMemoryTenantRepository tenants,
            InMemoryExternalIdentityRepository identities,
            InMemoryMembershipRepository memberships,
            InMemoryAdministrativeAuditRepository audits,
            StubAuthenticatedIdentityPort identityPort) {

        private Invitation invitation(String token, String email, UUID identityId) {
            Invitation invitation = new Invitation(
                    UUID.randomUUID(),
                    TENANT_ID,
                    NormalizedEmail.from(email),
                    MembershipRole.TENANT_USER,
                    InvitationStatus.PENDING,
                    InvitationTokenDigest.fromToken(token),
                    identityId,
                    NOW.plusSeconds(3600),
                    null,
                    null,
                    CREATOR_IDENTITY_ID,
                    NOW);
            invitations.save(invitation);
            return invitation;
        }
    }

    private static final class StubAuthenticatedIdentityPort
            implements AuthenticatedIdentityPort {

        private Optional<AuthenticatedIdentityProfile> profile = Optional.empty();

        @Override
        public Optional<AuthenticatedIdentityProfile> loadVerifiedProfile(
                AccessTokenContext accessTokenContext) {
            return profile;
        }
    }

    private static final class InMemoryInvitationRepository implements InvitationRepository {

        private final Map<UUID, Invitation> values = new HashMap<>();

        @Override
        public Optional<Invitation> findById(UUID invitationId) {
            return Optional.ofNullable(values.get(invitationId));
        }

        @Override
        public Optional<Invitation> findPendingByToken(String token, Instant now) {
            InvitationTokenDigest digest = InvitationTokenDigest.fromToken(token);
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

    private static final class InMemoryExternalIdentityRepository
            implements ExternalIdentityRepository {

        private final Map<UUID, ExternalIdentity> values = new HashMap<>();

        @Override
        public Optional<ExternalIdentity> findByProviderAndExternalSubject(
                ExternalSubject subject) {
            return values.values().stream()
                    .filter(identity -> identity.subject().equals(subject))
                    .findFirst();
        }

        @Override
        public ExternalIdentity save(ExternalIdentity identity) {
            values.put(identity.id(), identity);
            return identity;
        }
    }

    private static final class InMemoryMembershipRepository implements MembershipRepository {

        private final Map<UUID, Membership> values = new HashMap<>();

        @Override
        public List<Membership> findActiveByIdentityId(UUID identityId) {
            return values.values().stream()
                    .filter(membership -> membership.identityId().equals(identityId))
                    .filter(Membership::isActive)
                    .toList();
        }

        @Override
        public Membership save(Membership membership) {
            values.put(membership.id(), membership);
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
