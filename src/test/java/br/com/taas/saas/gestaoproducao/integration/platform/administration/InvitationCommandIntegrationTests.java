package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.CreateInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.ExpireInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationCommandService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationLinkResult;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.ResendInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.RevokeInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.InvitationConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;

@SpringBootTest
@ActiveProfiles("test")
@Import(InvitationCommandIntegrationTests.TestSupportConfiguration.class)
class InvitationCommandIntegrationTests {

    private static final UUID OWNER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ADMIN_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT_USER_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID ACTIVE_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-22T13:00:00Z");

    @Autowired
    private InvitationCommandService commandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailingAuditRepository auditRepository;

    @BeforeEach
    void resetAuditFailure() {
        auditRepository.fail = false;
    }

    @Test
    void createsPendingInvitationWithCopyableLinkAndOnlyDigestPersisted() {
        String email = uniqueEmail();

        InvitationLinkResult result = commandService.createInvitation(new CreateInvitationCommand(
                ADMIN_IDENTITY_ID,
                ACTIVE_TENANT_ID,
                email,
                NOW));
        String rawToken = result.link().substring("http://test.local/invitations/".length());
        String digest = jdbcTemplate.queryForObject(
                "SELECT token_digest FROM platform.invitation WHERE id = ?",
                String.class,
                result.invitation().id());

        assertThat(result.invitation().status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(result.invitation().expiresAt()).isEqualTo(NOW.plusSeconds(24 * 60 * 60));
        assertThat(result.link()).startsWith("http://test.local/invitations/");
        assertThat(digest).isEqualTo(InvitationTokenDigest.fromToken(rawToken).value());
        assertThat(digest).doesNotContain(rawToken);
    }

    @Test
    void rejectsDuplicateAndKeepsOnlyOnePendingInvitation() {
        String email = uniqueEmail();
        commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, ACTIVE_TENANT_ID, email, NOW));

        assertThatThrownBy(() -> commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID,
                ACTIVE_TENANT_ID,
                email.toUpperCase(),
                NOW.plusSeconds(1))))
                .isInstanceOf(InvitationConflictException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.invitation "
                        + "WHERE tenant_id = ? AND email = ? AND status = 'PENDING'",
                Long.class,
                ACTIVE_TENANT_ID,
                email)).isEqualTo(1L);
    }

    @Test
    void resendsByRevokingPreviousInvitationAndPersistingNewPendingLink() {
        String email = uniqueEmail();
        InvitationLinkResult previous = commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, ACTIVE_TENANT_ID, email, NOW));

        InvitationLinkResult replacement = commandService.resendInvitation(
                new ResendInvitationCommand(
                        ADMIN_IDENTITY_ID,
                        previous.invitation().id(),
                        NOW.plusSeconds(1)));

        assertThat(replacement.invitation().id()).isNotEqualTo(previous.invitation().id());
        assertThat(replacement.link()).isNotEqualTo(previous.link());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                previous.invitation().id())).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.invitation "
                        + "WHERE tenant_id = ? AND email = ? AND status = 'PENDING'",
                Long.class,
                ACTIVE_TENANT_ID,
                email)).isEqualTo(1L);
    }

    @Test
    void deniesSuspendedAndClosedTenants() {
        UUID suspendedTenantId = insertTenant(TenantStatus.SUSPENDED);
        UUID closedTenantId = insertTenant(TenantStatus.CLOSED);

        assertThatThrownBy(() -> commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID,
                suspendedTenantId,
                uniqueEmail(),
                NOW)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID,
                closedTenantId,
                uniqueEmail(),
                NOW)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void expiresInvitationAndAllowsNewInvitationForSameTenantAndEmail() {
        String email = uniqueEmail();
        InvitationLinkResult previous = commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, ACTIVE_TENANT_ID, email, NOW));

        commandService.expireInvitation(new ExpireInvitationCommand(
                OWNER_IDENTITY_ID,
                previous.invitation().id(),
                NOW.plusSeconds(24 * 60 * 60 + 1)));
        InvitationLinkResult replacement = commandService.createInvitation(
                new CreateInvitationCommand(
                        OWNER_IDENTITY_ID,
                        ACTIVE_TENANT_ID,
                        email,
                        NOW.plusSeconds(24 * 60 * 60 + 2)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                previous.invitation().id())).isEqualTo("EXPIRED");
        assertThat(replacement.invitation().status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void revokesInvitationAndRejectsNonPlatformActor() {
        InvitationLinkResult created = commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, ACTIVE_TENANT_ID, uniqueEmail(), NOW));

        assertThatThrownBy(() -> commandService.revokeInvitation(new RevokeInvitationCommand(
                TENANT_USER_IDENTITY_ID,
                created.invitation().id(),
                NOW.plusSeconds(1))))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                created.invitation().id())).isEqualTo("PENDING");

        commandService.revokeInvitation(new RevokeInvitationCommand(
                OWNER_IDENTITY_ID,
                created.invitation().id(),
                NOW.plusSeconds(2)));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                created.invitation().id())).isEqualTo("REVOKED");
    }

    @Test
    void rollsBackInvitationWhenAuditFails() {
        String email = uniqueEmail();
        auditRepository.fail = true;

        assertThatThrownBy(() -> commandService.createInvitation(new CreateInvitationCommand(
                OWNER_IDENTITY_ID, ACTIVE_TENANT_ID, email, NOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated audit failure");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.invitation WHERE email = ?",
                Long.class,
                email)).isZero();
    }

    private UUID insertTenant(TenantStatus status) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.tenant (id, name, status) VALUES (?, ?, ?)",
                tenantId,
                "T10 tenant " + tenantId,
                status.name());
        return tenantId;
    }

    private String uniqueEmail() {
        return "t10-" + UUID.randomUUID() + "@example.com";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfiguration {

        @Bean
        @Primary
        PlatformAuthorizationService invitationTestPlatformAuthorizationService() {
            PlatformRoleRepository roles = new PlatformRoleRepository() {
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
