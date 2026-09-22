package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.application.port.out.AuthenticatedIdentityPort;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.AcceptInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceResult;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

@SpringBootTest
@ActiveProfiles("test")
@Import(InvitationAcceptanceIntegrationTests.TestSupportConfiguration.class)
class InvitationAcceptanceIntegrationTests {

    private static final UUID CREATOR_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Instant NOW = Instant.parse("2026-09-22T14:00:00Z");

    @Autowired
    private InvitationAcceptanceService acceptanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubAuthenticatedIdentityPort identityPort;

    @Autowired
    private FailingAuditRepository auditRepository;

    @BeforeEach
    void resetFixtureState() {
        identityPort.profile = Optional.empty();
        auditRepository.fail = false;
    }

    @AfterEach
    void clearFailureFlag() {
        auditRepository.fail = false;
    }

    @Test
    void acceptsInvitationAndCommitsIdentityMembershipInvitationAndAudit() {
        String subject = unique("accept-subject");
        String email = unique("accept") + "@example.com";
        String token = unique("accept-token");
        UUID tenantId = insertTenant("acceptance");
        UUID invitationId = insertInvitation(tenantId, email, token, NOW.plusSeconds(3600));
        identityPort.verified(subject, email);

        InvitationAcceptanceResult result = acceptanceService.acceptInvitation(
                command(token, subject));

        assertThat(result.invitation().id()).isEqualTo(invitationId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                invitationId)).isEqualTo("ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.external_identity WHERE external_subject = ?",
                Long.class,
                subject)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership m "
                        + "JOIN platform.external_identity i ON i.id = m.identity_id "
                        + "WHERE i.external_subject = ? AND m.tenant_id = ? "
                        + "AND m.status = 'ACTIVE'",
                Long.class,
                subject,
                tenantId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.audit_event WHERE target_id IN (?, ?)",
                Long.class,
                invitationId,
                result.membership().id())).isEqualTo(2L);
    }

    @Test
    void secondTenantIsDeniedAndCurrentMembershipRemainsUnchanged() {
        String subject = unique("second-tenant-subject");
        String email = unique("second-tenant") + "@example.com";
        String token = unique("second-tenant-token");
        UUID currentTenantId = insertTenant("current");
        UUID targetTenantId = insertTenant("target");
        UUID identityId = UUID.randomUUID();
        insertIdentity(identityId, subject);
        insertActiveMembership(identityId, currentTenantId);
        UUID invitationId = insertInvitation(targetTenantId, email, token, NOW.plusSeconds(3600));
        identityPort.verified(subject, email);

        assertThatThrownBy(() -> acceptanceService.acceptInvitation(
                command(token, subject)))
                .isInstanceOf(InvitationAcceptanceException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                invitationId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership WHERE identity_id = ? AND status = 'ACTIVE'",
                Long.class,
                identityId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM platform.membership WHERE identity_id = ? AND status = 'ACTIVE'",
                UUID.class,
                identityId)).isEqualTo(currentTenantId);
    }

    @Test
    void auditFailureRollsBackNewIdentityMembershipAndInvitationAcceptance() {
        String subject = unique("rollback-subject");
        String email = unique("rollback") + "@example.com";
        String token = unique("rollback-token");
        UUID tenantId = insertTenant("rollback");
        UUID invitationId = insertInvitation(tenantId, email, token, NOW.plusSeconds(3600));
        identityPort.verified(subject, email);
        auditRepository.fail = true;

        assertThatThrownBy(() -> acceptanceService.acceptInvitation(
                command(token, subject)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated audit failure");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM platform.invitation WHERE id = ?",
                String.class,
                invitationId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.external_identity WHERE external_subject = ?",
                Long.class,
                subject)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.audit_event WHERE target_id = ?",
                Long.class,
                invitationId)).isZero();
    }

    @Test
    void concurrentAcceptanceOfTheSameTokenCommitsOnlyOneMembership() throws Exception {
        String subject = unique("concurrent-subject");
        String email = unique("concurrent") + "@example.com";
        String token = unique("concurrent-token");
        UUID tenantId = insertTenant("concurrent");
        insertInvitation(tenantId, email, token, NOW.plusSeconds(3600));
        identityPort.verified(subject, email);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> acceptOrFailure(token, subject));
            Future<Object> second = executor.submit(() -> acceptOrFailure(token, subject));
            List<Object> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(results).anyMatch(InvitationAcceptanceResult.class::isInstance);
            assertThat(results).anyMatch(InvitationAcceptanceException.class::isInstance);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM platform.membership m "
                            + "JOIN platform.external_identity i ON i.id = m.identity_id "
                            + "WHERE i.external_subject = ? AND m.status = 'ACTIVE'",
                    Long.class,
                    subject)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object acceptOrFailure(String token, String subject) {
        try {
            return acceptanceService.acceptInvitation(command(token, subject));
        } catch (InvitationAcceptanceException exception) {
            return exception;
        }
    }

    private AcceptInvitationCommand command(String token, String subject) {
        return new AcceptInvitationCommand(
                token,
                new AccessTokenContext(ExternalSubject.fromSupabase(subject), "validated-token"),
                NOW);
    }

    private UUID insertTenant(String label) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.tenant (id, name, status, created_at) VALUES (?, ?, 'ACTIVE', ?)",
                tenantId,
                "T11 " + label + " " + tenantId,
                timestamp(NOW));
        return tenantId;
    }

    private UUID insertInvitation(
            UUID tenantId,
            String email,
            String token,
            Instant expiresAt) {
        UUID invitationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.invitation "
                        + "(id, tenant_id, email, role, status, token_digest, "
                        + "identity_id, expires_at, accepted_at, revoked_at, created_by, created_at) "
                        + "VALUES (?, ?, ?, 'TENANT_USER', 'PENDING', ?, NULL, ?, NULL, NULL, ?, ?)",
                invitationId,
                tenantId,
                NormalizedEmail.from(email).value(),
                InvitationTokenDigest.fromToken(token).value(),
                timestamp(expiresAt),
                CREATOR_IDENTITY_ID,
                timestamp(NOW));
        return invitationId;
    }

    private void insertIdentity(UUID identityId, String subject) {
        jdbcTemplate.update(
                "INSERT INTO platform.external_identity "
                        + "(id, provider, external_subject, status, created_at) "
                        + "VALUES (?, 'SUPABASE', ?, 'ACTIVE', ?)",
                identityId,
                subject,
                timestamp(NOW));
    }

    private void insertActiveMembership(UUID identityId, UUID tenantId) {
        jdbcTemplate.update(
                "INSERT INTO platform.membership "
                        + "(id, identity_id, tenant_id, status, role, created_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'TENANT_USER', ?)",
                UUID.randomUUID(),
                identityId,
                tenantId,
                timestamp(NOW));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfiguration {

        @Bean
        @Primary
        StubAuthenticatedIdentityPort invitationAcceptanceIdentityPort() {
            return new StubAuthenticatedIdentityPort();
        }

        @Bean
        @Primary
        FailingAuditRepository invitationAcceptanceAuditRepository(
                @Qualifier("jpaAdministrativeAuditRepository")
                AdministrativeAuditRepository delegate) {
            return new FailingAuditRepository(delegate);
        }
    }

    static final class StubAuthenticatedIdentityPort implements AuthenticatedIdentityPort {

        private volatile Optional<AuthenticatedIdentityProfile> profile = Optional.empty();

        void verified(String subject, String email) {
            profile = Optional.of(new AuthenticatedIdentityProfile(
                    ExternalSubject.fromSupabase(subject),
                    NormalizedEmail.from(email),
                    true));
        }

        @Override
        public Optional<AuthenticatedIdentityProfile> loadVerifiedProfile(
                AccessTokenContext accessTokenContext) {
            return profile;
        }
    }

    static final class FailingAuditRepository implements AdministrativeAuditRepository {

        private final AdministrativeAuditRepository delegate;
        private volatile boolean fail;

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
