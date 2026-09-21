package br.com.taas.saas.gestaoproducao.integration.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentityStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@SpringBootTest
@ActiveProfiles("test")
class IdentityMembershipPersistenceIntegrationTests {

    private static final UUID IDENTITY_A =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID IDENTITY_BLOCKED =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ExternalIdentityRepository externalIdentityRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsIdentityByExternalSubjectAndKeepsInternalIdSeparate() {
        var identity = externalIdentityRepository
                .findByProviderAndExternalSubject(ExternalSubject.fromSupabase("test-subject-a"));

        assertThat(identity).isPresent();
        assertThat(identity.orElseThrow().id()).isEqualTo(IDENTITY_A);
        assertThat(identity.orElseThrow().subject())
                .isEqualTo(ExternalSubject.fromSupabase("test-subject-a"));
        assertThat(identity.orElseThrow().id().toString())
                .isNotEqualTo(identity.orElseThrow().subject().value());
        assertThat(identity.orElseThrow().isActive()).isTrue();
    }

    @Test
    void findsActiveMembershipsByIdentityWithoutAcceptingATenantParameter() {
        var memberships = membershipRepository.findActiveByIdentityId(IDENTITY_A);

        assertThat(memberships).hasSize(1);
        assertThat(memberships.getFirst().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(memberships.getFirst().tenantId()).isEqualTo(TENANT_A);
        assertThat(memberships.getFirst().role().value()).isEqualTo("TENANT_USER");
    }

    @Test
    void doesNotReturnPendingOrRevokedMembershipsAsOperationalMemberships() {
        var identity = externalIdentityRepository
                .findByProviderAndExternalSubject(ExternalSubject.fromSupabase("blocked-subject"))
                .orElseThrow();

        assertThat(identity.id()).isEqualTo(IDENTITY_BLOCKED);
        assertThat(identity.status()).isEqualTo(ExternalIdentityStatus.BLOCKED);
        assertThat(identity.isActive()).isFalse();
        assertThat(membershipRepository.findActiveByIdentityId(IDENTITY_BLOCKED)).isEmpty();
    }

    @Test
    void loadsTenantByInternalIdAndPreservesItsStatus() {
        var tenant = tenantRepository.findById(TENANT_A).orElseThrow();

        assertThat(tenant.id()).isEqualTo(TENANT_A);
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.isAvailable()).isTrue();
    }

    @Test
    void keepsTheDatabaseConstraintForOneActiveMembershipPerIdentity() {
        String predicate = jdbcTemplate.queryForObject(
                "SELECT pg_get_expr(i.indpred, i.indrelid) "
                        + "FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'platform' "
                        + "AND c.relname = 'membership_one_active_per_identity_uq'",
                String.class);

        assertThat(predicate).contains("status").contains("ACTIVE");
    }
}
