package br.com.taas.saas.gestaoproducao.integration.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.InvitationConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

@SpringBootTest
@ActiveProfiles("test")
class InvitationPersistenceIntegrationTests {

    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID IDENTITY_A =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID IDENTITY_B =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void normalizesEmailAndPersistsOnlyTokenDigest() {
        String rawToken = "clear-token-that-must-not-be-persisted";
        Invitation invitation = pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                "  User@Example.COM ",
                rawToken,
                CREATED_AT.plusSeconds(24 * 60 * 60));

        invitationRepository.save(invitation);

        Invitation persisted = invitationRepository.findById(invitation.id()).orElseThrow();
        String persistedDigest = jdbcTemplate.queryForObject(
                "SELECT token_digest FROM platform.invitation WHERE id = ?",
                String.class,
                invitation.id());

        assertThat(persisted.email().value()).isEqualTo("user@example.com");
        assertThat(persisted.role()).isEqualTo(MembershipRole.TENANT_USER);
        assertThat(persistedDigest).isEqualTo(InvitationTokenDigest.fromToken(rawToken).value());
        assertThat(persistedDigest).isNotEqualTo(rawToken);
        assertThat(persistedDigest).doesNotContain(rawToken);
    }

    @Test
    @Transactional
    void findsOnlyTheValidPendingInvitationByTokenAndNormalizedEmail() {
        Invitation valid = pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                "valid@example.com",
                "valid-token",
                NOW.plusSeconds(60 * 60));
        Invitation expiredByTime = pendingInvitation(
                UUID.randomUUID(),
                TENANT_B,
                "expired@example.com",
                "expired-token",
                NOW.minusSeconds(1));

        invitationRepository.save(valid);
        invitationRepository.save(expiredByTime);

        assertThat(invitationRepository.findPendingByToken("valid-token", NOW))
                .isPresent()
                .get()
                .extracting(Invitation::id)
                .isEqualTo(valid.id());
        assertThat(invitationRepository.findPendingByTenantAndEmail(
                TENANT_A,
                " VALID@EXAMPLE.COM ",
                NOW))
                .isPresent()
                .get()
                .extracting(Invitation::id)
                .isEqualTo(valid.id());
        assertThat(invitationRepository.findPendingByToken("expired-token", NOW)).isEmpty();
    }

    @Test
    @Transactional
    void doesNotReturnAcceptedOrRevokedInvitationsAsPending() {
        Invitation accepted = pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                "accepted@example.com",
                "accepted-token",
                NOW.plusSeconds(60 * 60))
                .accept(IDENTITY_A, NOW.minusSeconds(30 * 60));
        Invitation revoked = pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                "revoked@example.com",
                "revoked-token",
                NOW.plusSeconds(60 * 60))
                .revoke(NOW.minusSeconds(15 * 60));

        invitationRepository.save(accepted);
        invitationRepository.save(revoked);

        assertThat(invitationRepository.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitationRepository.findById(revoked.id()).orElseThrow().status())
                .isEqualTo(InvitationStatus.REVOKED);
        assertThat(invitationRepository.findPendingByToken("accepted-token", NOW)).isEmpty();
        assertThat(invitationRepository.findPendingByToken("revoked-token", NOW)).isEmpty();
    }

    @Test
    @Transactional
    void translatesDuplicatePendingTenantEmailIntoConflict() {
        invitationRepository.save(pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                "duplicate@example.com",
                "first-token",
                NOW.plusSeconds(60 * 60)));

        assertThatThrownBy(() -> invitationRepository.save(pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                " DUPLICATE@EXAMPLE.COM ",
                "second-token",
                NOW.plusSeconds(60 * 60))))
                .isInstanceOf(InvitationConflictException.class)
                .hasMessageContaining("pending invitation");
    }

    @Test
    @Transactional
    void allowsResendAfterPreviousInvitationIsRevoked() {
        Invitation previous = pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                "resend@example.com",
                "old-token",
                NOW.plusSeconds(60 * 60));
        invitationRepository.save(previous.revoke(NOW.minusSeconds(60)));
        Invitation replacement = pendingInvitation(
                UUID.randomUUID(),
                TENANT_A,
                " RESEND@EXAMPLE.COM ",
                "new-token",
                NOW.plusSeconds(60 * 60));

        invitationRepository.save(replacement);

        assertThat(invitationRepository.findPendingByTenantAndEmail(
                TENANT_A,
                "resend@example.com",
                NOW))
                .isPresent()
                .get()
                .extracting(Invitation::id)
                .isEqualTo(replacement.id());
        assertThat(invitationRepository.findPendingByToken("old-token", NOW)).isEmpty();
        assertThat(invitationRepository.findPendingByToken("new-token", NOW)).isPresent();
    }

    private Invitation pendingInvitation(
            UUID id,
            UUID tenantId,
            String email,
            String rawToken,
            Instant expiresAt) {
        return new Invitation(
                id,
                tenantId,
                NormalizedEmail.from(email),
                MembershipRole.TENANT_USER,
                InvitationStatus.PENDING,
                InvitationTokenDigest.fromToken(rawToken),
                null,
                expiresAt,
                null,
                null,
                IDENTITY_A,
                CREATED_AT);
    }
}
