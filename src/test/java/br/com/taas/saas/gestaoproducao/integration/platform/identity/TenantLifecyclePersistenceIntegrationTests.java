package br.com.taas.saas.gestaoproducao.integration.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@SpringBootTest
@ActiveProfiles("test")
class TenantLifecyclePersistenceIntegrationTests {

    private static final UUID IDENTITY_BLOCKED =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void persistsTenantWithoutCreatingMembership() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant(
                tenantId,
                "Cozinha Central",
                TenantStatus.ACTIVE,
                CREATED_AT);

        tenantRepository.save(tenant);

        Tenant persisted = tenantRepository.findById(tenantId).orElseThrow();
        Long membershipCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform.membership WHERE tenant_id = ?",
                Long.class,
                tenantId);

        assertThat(persisted.name()).isEqualTo("Cozinha Central");
        assertThat(persisted.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(membershipCount).isZero();
    }

    @Test
    @Transactional
    void persistsSuspendReactivateAndTerminalCloseTransitions() {
        UUID tenantId = UUID.randomUUID();
        Tenant active = new Tenant(
                tenantId,
                "Marmitas da Semana",
                TenantStatus.ACTIVE,
                CREATED_AT);
        tenantRepository.save(active);

        Tenant suspended = tenantRepository.save(active.suspend());
        Tenant reactivated = tenantRepository.save(suspended.reactivate());
        Tenant closed = tenantRepository.save(reactivated.close());

        assertThat(suspended.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(suspended.isAvailable()).isFalse();
        assertThat(reactivated.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(reactivated.isAvailable()).isTrue();
        assertThat(closed.status()).isEqualTo(TenantStatus.CLOSED);
        assertThat(closed.isAvailable()).isFalse();
        assertThatThrownBy(closed::suspend)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(closed::reactivate)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(closed::close)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    void revokesMembershipWithoutDeletingHistoryAndAllowsReplacement() {
        UUID membershipId = UUID.randomUUID();
        Membership active = membership(
                membershipId,
                IDENTITY_BLOCKED,
                TENANT_A,
                MembershipStatus.ACTIVE,
                null);
        membershipRepository.save(active);

        Membership revoked = membershipRepository.save(
                active.revoke(CREATED_AT.plusSeconds(60)));
        Membership persisted = membershipRepository.findById(membershipId).orElseThrow();
        Membership replacement = membershipRepository.save(membership(
                UUID.randomUUID(),
                IDENTITY_BLOCKED,
                TENANT_B,
                MembershipStatus.ACTIVE,
                null));

        assertThat(revoked.status()).isEqualTo(MembershipStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(CREATED_AT.plusSeconds(60));
        assertThat(persisted.status()).isEqualTo(MembershipStatus.REVOKED);
        assertThat(membershipRepository.findByIdentityId(IDENTITY_BLOCKED))
                .extracting(Membership::id)
                .contains(membershipId);
        assertThat(membershipRepository.findActiveByIdentityId(IDENTITY_BLOCKED))
                .extracting(Membership::id)
                .containsExactly(replacement.id());
    }

    @Test
    @Transactional
    void databaseRejectsSecondActiveMembershipForSameIdentity() {
        UUID identityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO platform.external_identity "
                        + "(id, provider, external_subject, status) VALUES (?, 'SUPABASE', ?, 'ACTIVE')",
                identityId,
                "t6-" + identityId);

        membershipRepository.save(membership(
                UUID.randomUUID(),
                identityId,
                TENANT_A,
                MembershipStatus.ACTIVE,
                null));

        assertThatThrownBy(() -> membershipRepository.save(membership(
                UUID.randomUUID(),
                identityId,
                TENANT_B,
                MembershipStatus.ACTIVE,
                null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Membership membership(
            UUID id,
            UUID identityId,
            UUID tenantId,
            MembershipStatus status,
            Instant revokedAt) {
        return new Membership(
                id,
                identityId,
                tenantId,
                status,
                MembershipRole.TENANT_USER,
                CREATED_AT,
                revokedAt);
    }
}
