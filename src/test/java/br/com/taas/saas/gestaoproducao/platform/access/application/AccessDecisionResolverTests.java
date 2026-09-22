package br.com.taas.saas.gestaoproducao.platform.access.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentityStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantAccessContext;

class AccessDecisionResolverTests {

    private static final ExternalSubject SUBJECT = ExternalSubject.fromSupabase("user-123");
    private static final UUID IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void resolvesExactlyOneActiveTenantMembership() {
        var decision = resolver(
                identity(ExternalIdentityStatus.ACTIVE),
                List.of(membership(MembershipStatus.ACTIVE, MembershipRole.TENANT_USER, null)),
                tenant(TenantStatus.ACTIVE))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.TENANT_ACCESS);
        TenantAccessContext context = decision.tenantContext();
        assertThat(context.externalSubject()).isEqualTo(SUBJECT);
        assertThat(context.identityId()).isEqualTo(IDENTITY_ID);
        assertThat(context.tenantId().value()).isEqualTo(TENANT_ID);
        assertThat(context.role()).isEqualTo(MembershipRole.TENANT_USER);
    }

    @Test
    void resolvesPlatformAccessFromPlatformRoleWithoutCreatingTenantContext() {
        var decision = new AccessDecisionResolver(
                subject -> Optional.of(identity(ExternalIdentityStatus.ACTIVE)),
                identityId -> List.of(membership(
                        MembershipStatus.ACTIVE,
                        MembershipRole.TENANT_USER,
                        null)),
                tenantId -> {
                    throw new AssertionError("platform access must not resolve a tenant");
                },
                new PlatformAuthorizationService(platformRoleRepository(List.of(
                        platformRole(PlatformRole.PLATFORM_ADMIN)))))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.PLATFORM_ACCESS);
        assertThat(decision.tenantContext()).isNull();
    }

    @Test
    void returnsNotProvisionedWhenExternalIdentityIsMissing() {
        var decision = resolver(null, List.of(), tenant(TenantStatus.ACTIVE)).resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.NOT_PROVISIONED);
        assertThat(decision.tenantContext()).isNull();
    }

    @Test
    void returnsNotProvisionedWhenThereAreNoActiveMemberships() {
        var decision = resolver(
                identity(ExternalIdentityStatus.ACTIVE),
                List.of(),
                tenant(TenantStatus.ACTIVE))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.NOT_PROVISIONED);
        assertThat(decision.tenantContext()).isNull();
    }

    @Test
    void returnsNotProvisionedWhenIdentityIsBlocked() {
        var decision = resolver(
                identity(ExternalIdentityStatus.BLOCKED),
                List.of(membership(MembershipStatus.ACTIVE, MembershipRole.TENANT_USER, null)),
                tenant(TenantStatus.ACTIVE))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.NOT_PROVISIONED);
        assertThat(decision.tenantContext()).isNull();
    }

    @Test
    void returnsNotProvisionedWhenOnlyRevokedMembershipIsAvailable() {
        var decision = resolver(
                identity(ExternalIdentityStatus.ACTIVE),
                List.of(membership(
                        MembershipStatus.REVOKED,
                        MembershipRole.TENANT_USER,
                        CREATED_AT)),
                tenant(TenantStatus.ACTIVE))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.NOT_PROVISIONED);
        assertThat(decision.tenantContext()).isNull();
    }

    @Test
    void blocksAmbiguousActiveMembershipsWithoutSelectingATenant() {
        var anotherTenantId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var decision = new AccessDecisionResolver(
                subject -> Optional.of(identity(ExternalIdentityStatus.ACTIVE)),
                identityId -> List.of(
                        membership(MembershipStatus.ACTIVE, MembershipRole.TENANT_USER, null),
                        new Membership(
                                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                                IDENTITY_ID,
                                anotherTenantId,
                                MembershipStatus.ACTIVE,
                                MembershipRole.TENANT_USER,
                                CREATED_AT,
                                null)),
                tenantId -> {
                    throw new AssertionError("ambiguous access must not resolve a tenant");
                },
                new PlatformAuthorizationService(platformRoleRepository(List.of())))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.AMBIGUOUS_MEMBERSHIP);
        assertThat(decision.tenantContext()).isNull();
    }

    @Test
    void blocksWhenTheResolvedTenantIsUnavailable() {
        var decision = resolver(
                identity(ExternalIdentityStatus.ACTIVE),
                List.of(membership(MembershipStatus.ACTIVE, MembershipRole.TENANT_USER, null)),
                tenant(TenantStatus.SUSPENDED))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.NOT_PROVISIONED);
        assertThat(decision.tenantContext()).isNull();
    }

    @Test
    void blocksWhenTheResolvedTenantDoesNotExist() {
        var decision = new AccessDecisionResolver(
                subject -> Optional.of(identity(ExternalIdentityStatus.ACTIVE)),
                identityId -> List.of(membership(
                        MembershipStatus.ACTIVE,
                        MembershipRole.TENANT_USER,
                        null)),
                tenantId -> Optional.empty(),
                new PlatformAuthorizationService(platformRoleRepository(List.of())))
                .resolve(SUBJECT);

        assertThat(decision.type()).isEqualTo(AccessDecisionType.NOT_PROVISIONED);
        assertThat(decision.tenantContext()).isNull();
    }

    private static AccessDecisionResolver resolver(
            ExternalIdentity identity,
            List<Membership> memberships,
            Tenant tenant) {
        ExternalIdentityRepository identities = subject -> Optional.ofNullable(identity);
        MembershipRepository membershipRepository = identityId -> memberships;
        TenantRepository tenantRepository = tenantId -> Optional.ofNullable(tenant);
        return new AccessDecisionResolver(
                identities,
                membershipRepository,
                tenantRepository,
                new PlatformAuthorizationService(platformRoleRepository(List.of())));
    }

    private static PlatformRoleRepository platformRoleRepository(
            List<PlatformRoleAssignment> assignments) {
        return new PlatformRoleRepository() {
            @Override
            public List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId) {
                return assignments;
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
                return assignment;
            }
        };
    }

    private static PlatformRoleAssignment platformRole(PlatformRole role) {
        return new PlatformRoleAssignment(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                IDENTITY_ID,
                role,
                PlatformRoleStatus.ACTIVE,
                CREATED_AT,
                null);
    }

    private static ExternalIdentity identity(ExternalIdentityStatus status) {
        return new ExternalIdentity(IDENTITY_ID, SUBJECT, status, CREATED_AT);
    }

    private static Membership membership(
            MembershipStatus status,
            MembershipRole role,
            Instant revokedAt) {
        return new Membership(
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                IDENTITY_ID,
                TENANT_ID,
                status,
                role,
                CREATED_AT,
                revokedAt);
    }

    private static Tenant tenant(TenantStatus status) {
        return new Tenant(TENANT_ID, status, CREATED_AT);
    }
}
