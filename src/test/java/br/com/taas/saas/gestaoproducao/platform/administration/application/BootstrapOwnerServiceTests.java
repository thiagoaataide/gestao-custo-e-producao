package br.com.taas.saas.gestaoproducao.platform.administration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.config.PlatformBootstrapProperties;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentityStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

class BootstrapOwnerServiceTests {

    private static final ExternalSubject AUTHORIZED_SUBJECT =
            ExternalSubject.fromSupabase("owner-subject");
    private static final ExternalSubject DIFFERENT_SUBJECT =
            ExternalSubject.fromSupabase("different-subject");
    private static final UUID OWNER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void createsTheFirstOwnerAndAuditEventForTheAuthorizedSubject() {
        var identities = new InMemoryExternalIdentityRepository();
        var roles = new InMemoryPlatformRoleRepository();
        var audits = new InMemoryAdministrativeAuditRepository();

        var owner = service(identities, roles, audits)
                .bootstrapOwner(AUTHORIZED_SUBJECT, NOW);

        assertThat(owner.role()).isEqualTo(PlatformRole.PLATFORM_OWNER);
        assertThat(owner.identityId()).isEqualTo(identities.findByProviderAndExternalSubject(
                AUTHORIZED_SUBJECT).orElseThrow().id());
        assertThat(roles.assignments).containsExactly(owner);
        assertThat(audits.events).singleElement()
                .extracting(AuditEvent::action)
                .isEqualTo(AuditAction.BOOTSTRAP_OWNER);
    }

    @Test
    void repeatedBootstrapIsIdempotentAndDoesNotCreateAnotherOwner() {
        var identities = new InMemoryExternalIdentityRepository();
        var roles = new InMemoryPlatformRoleRepository();
        var audits = new InMemoryAdministrativeAuditRepository();
        var service = service(identities, roles, audits);

        var first = service.bootstrapOwner(AUTHORIZED_SUBJECT, NOW);
        var repeated = service.bootstrapOwner(AUTHORIZED_SUBJECT, NOW.plusSeconds(1));

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(roles.assignments).containsExactly(first);
        assertThat(audits.events).hasSize(2);
        assertThat(identities.identities).hasSize(1);
    }

    @Test
    void unauthorizedSubjectCannotBootstrapOrCreateIdentity() {
        var identities = new InMemoryExternalIdentityRepository();
        var roles = new InMemoryPlatformRoleRepository();
        var audits = new InMemoryAdministrativeAuditRepository();

        assertThatThrownBy(() -> service(identities, roles, audits)
                .bootstrapOwner(DIFFERENT_SUBJECT, NOW))
                .isInstanceOf(PlatformBootstrapDeniedException.class);

        assertThat(identities.identities).isEmpty();
        assertThat(roles.assignments).isEmpty();
        assertThat(audits.events).isEmpty();
    }

    @Test
    void authorizedSubjectCannotReplaceAnExistingOwner() {
        var identities = new InMemoryExternalIdentityRepository();
        identities.save(identity(OWNER_IDENTITY_ID, AUTHORIZED_SUBJECT, ExternalIdentityStatus.ACTIVE));
        identities.save(identity(OTHER_IDENTITY_ID, DIFFERENT_SUBJECT, ExternalIdentityStatus.ACTIVE));
        var roles = new InMemoryPlatformRoleRepository();
        roles.assignments.add(owner(OTHER_IDENTITY_ID));
        var audits = new InMemoryAdministrativeAuditRepository();

        assertThatThrownBy(() -> service(identities, roles, audits)
                .bootstrapOwner(AUTHORIZED_SUBJECT, NOW))
                .isInstanceOf(PlatformBootstrapDeniedException.class);

        assertThat(roles.assignments).hasSize(1);
        assertThat(audits.events).isEmpty();
    }

    @Test
    void blockedAuthorizedIdentityCannotBecomeOwner() {
        var identities = new InMemoryExternalIdentityRepository();
        identities.save(identity(OWNER_IDENTITY_ID, AUTHORIZED_SUBJECT, ExternalIdentityStatus.BLOCKED));
        var roles = new InMemoryPlatformRoleRepository();
        var audits = new InMemoryAdministrativeAuditRepository();

        assertThatThrownBy(() -> service(identities, roles, audits)
                .bootstrapOwner(AUTHORIZED_SUBJECT, NOW))
                .isInstanceOf(PlatformBootstrapDeniedException.class);

        assertThat(roles.assignments).isEmpty();
        assertThat(audits.events).isEmpty();
    }

    private static BootstrapOwnerService service(
            InMemoryExternalIdentityRepository identities,
            InMemoryPlatformRoleRepository roles,
            InMemoryAdministrativeAuditRepository audits) {
        return new BootstrapOwnerService(
                roles,
                identities,
                audits,
                new PlatformBootstrapProperties(AUTHORIZED_SUBJECT.value()));
    }

    private static ExternalIdentity identity(
            UUID id,
            ExternalSubject subject,
            ExternalIdentityStatus status) {
        return new ExternalIdentity(id, subject, status, NOW);
    }

    private static PlatformRoleAssignment owner(UUID identityId) {
        return new PlatformRoleAssignment(
                UUID.randomUUID(),
                identityId,
                PlatformRole.PLATFORM_OWNER,
                PlatformRoleStatus.ACTIVE,
                NOW,
                null);
    }

    private static final class InMemoryExternalIdentityRepository
            implements ExternalIdentityRepository {

        private final List<ExternalIdentity> identities = new ArrayList<>();

        @Override
        public Optional<ExternalIdentity> findByProviderAndExternalSubject(
                ExternalSubject subject) {
            return identities.stream()
                    .filter(identity -> identity.subject().equals(subject))
                    .findFirst();
        }

        @Override
        public ExternalIdentity save(ExternalIdentity identity) {
            identities.add(identity);
            return identity;
        }
    }

    private static final class InMemoryPlatformRoleRepository
            implements PlatformRoleRepository {

        private final List<PlatformRoleAssignment> assignments = new ArrayList<>();

        @Override
        public List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId) {
            return assignments.stream()
                    .filter(assignment -> assignment.identityId().equals(identityId))
                    .filter(PlatformRoleAssignment::isActive)
                    .toList();
        }

        @Override
        public Optional<PlatformRoleAssignment> findActiveOwner() {
            return assignments.stream()
                    .filter(PlatformRoleAssignment::isActive)
                    .filter(assignment -> assignment.role().isOwner())
                    .findFirst();
        }

        @Override
        public PlatformRoleAssignment save(PlatformRoleAssignment assignment) {
            assignments.add(assignment);
            return assignment;
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
