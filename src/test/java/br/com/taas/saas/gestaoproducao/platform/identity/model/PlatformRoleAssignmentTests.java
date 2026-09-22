package br.com.taas.saas.gestaoproducao.platform.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlatformRoleAssignmentTests {

    private static final UUID ASSIGNMENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Instant CREATED_AT = Instant.parse("2026-09-22T12:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-09-22T13:00:00Z");

    @Test
    void acceptsKnownPlatformRolesAndRejectsBlankOrUnknownValues() {
        assertThat(PlatformRole.from("PLATFORM_OWNER")).isEqualTo(PlatformRole.PLATFORM_OWNER);
        assertThat(PlatformRole.from("platform_admin")).isEqualTo(PlatformRole.PLATFORM_ADMIN);

        assertThatThrownBy(() -> PlatformRole.from("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> PlatformRole.from("TENANT_USER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void allowsOnlyOneActiveOwnerAndAllowsARevokedOwnerInHistory() {
        PlatformRoleAssignment activeOwner = assignment(
                ASSIGNMENT_ID,
                PlatformRole.PLATFORM_OWNER,
                PlatformRoleStatus.ACTIVE,
                null);
        PlatformRoleAssignment revokedOwner = activeOwner.revoke(REVOKED_AT);

        PlatformRoleAssignmentPolicy.ensureSingleActiveOwner(List.of(activeOwner));
        PlatformRoleAssignmentPolicy.ensureSingleActiveOwner(List.of(revokedOwner, activeOwner));

        assertThatThrownBy(() -> PlatformRoleAssignmentPolicy.ensureSingleActiveOwner(
                List.of(activeOwner, assignment(
                        UUID.fromString("00000000-0000-0000-0000-000000000302"),
                        PlatformRole.PLATFORM_OWNER,
                        PlatformRoleStatus.ACTIVE,
                        null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only one active platform owner");
    }

    @Test
    void revocationPreservesIdentityRoleAndHistory() {
        PlatformRoleAssignment assignment = assignment(
                ASSIGNMENT_ID,
                PlatformRole.PLATFORM_ADMIN,
                PlatformRoleStatus.ACTIVE,
                null);

        PlatformRoleAssignment revoked = assignment.revoke(REVOKED_AT);

        assertThat(revoked.status()).isEqualTo(PlatformRoleStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(REVOKED_AT);
        assertThat(revoked.id()).isEqualTo(ASSIGNMENT_ID);
        assertThat(revoked.identityId()).isEqualTo(IDENTITY_ID);
        assertThat(revoked.role()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(revoked.createdAt()).isEqualTo(CREATED_AT);
        assertThatThrownBy(() -> revoked.revoke(Instant.parse("2026-09-22T14:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active platform role");
    }

    @Test
    void onlyAnActiveOwnerCanManagePlatformRoles() {
        PlatformRoleAssignment owner = assignment(
                ASSIGNMENT_ID,
                PlatformRole.PLATFORM_OWNER,
                PlatformRoleStatus.ACTIVE,
                null);
        PlatformRoleAssignment admin = assignment(
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                PlatformRole.PLATFORM_ADMIN,
                PlatformRoleStatus.ACTIVE,
                null);

        assertThat(PlatformRoleAssignmentPolicy.canManagePlatformRoles(owner)).isTrue();
        assertThat(PlatformRoleAssignmentPolicy.canManagePlatformRoles(admin)).isFalse();
        assertThat(PlatformRoleAssignmentPolicy.canManagePlatformRoles(owner.revoke(REVOKED_AT)))
                .isFalse();
    }

    private static PlatformRoleAssignment assignment(
            UUID id,
            PlatformRole role,
            PlatformRoleStatus status,
            Instant revokedAt) {
        return new PlatformRoleAssignment(id, IDENTITY_ID, role, status, CREATED_AT, revokedAt);
    }
}
