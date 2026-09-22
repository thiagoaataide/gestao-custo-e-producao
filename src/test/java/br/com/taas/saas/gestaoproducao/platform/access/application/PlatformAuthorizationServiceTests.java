package br.com.taas.saas.gestaoproducao.platform.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

class PlatformAuthorizationServiceTests {

    private static final UUID IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Instant CREATED_AT = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void ownerHasPriorityAndCanManagePlatformRoles() {
        var service = service(
                assignment(PlatformRole.PLATFORM_ADMIN, PlatformRoleStatus.ACTIVE),
                assignment(PlatformRole.PLATFORM_OWNER, PlatformRoleStatus.ACTIVE));

        assertThat(service.resolveActiveRole(IDENTITY_ID))
                .get()
                .extracting(PlatformRoleAssignment::role)
                .isEqualTo(PlatformRole.PLATFORM_OWNER);
        assertThat(service.hasPlatformAccess(IDENTITY_ID)).isTrue();
        assertThat(service.canManagePlatformRoles(IDENTITY_ID)).isTrue();
    }

    @Test
    void adminHasPlatformAccessButCannotManagePlatformRoles() {
        var service = service(assignment(PlatformRole.PLATFORM_ADMIN, PlatformRoleStatus.ACTIVE));

        assertThat(service.hasPlatformAccess(IDENTITY_ID)).isTrue();
        assertThat(service.canManagePlatformRoles(IDENTITY_ID)).isFalse();
        assertThatThrownBy(() -> service.requireOwner(IDENTITY_ID))
                .isInstanceOf(PlatformAuthorizationDeniedException.class);
    }

    @Test
    void revokedOrMissingRoleDoesNotGrantPlatformAccess() {
        var service = service(assignment(PlatformRole.PLATFORM_OWNER, PlatformRoleStatus.REVOKED));

        assertThat(service.resolveActiveRole(IDENTITY_ID)).isEmpty();
        assertThat(service.hasPlatformAccess(IDENTITY_ID)).isFalse();
        assertThatThrownBy(() -> service.requirePlatformAccess(IDENTITY_ID))
                .isInstanceOf(PlatformAuthorizationDeniedException.class);
    }

    private static PlatformAuthorizationService service(PlatformRoleAssignment... assignments) {
        PlatformRoleRepository repository = new PlatformRoleRepository() {
            @Override
            public List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId) {
                return List.of(assignments);
            }

            @Override
            public Optional<PlatformRoleAssignment> findActiveOwner() {
                return List.of(assignments).stream()
                        .filter(PlatformRoleAssignment::isActive)
                        .filter(assignment -> assignment.role().isOwner())
                        .findFirst();
            }

            @Override
            public PlatformRoleAssignment save(PlatformRoleAssignment assignment) {
                return assignment;
            }
        };
        return new PlatformAuthorizationService(repository);
    }

    private static PlatformRoleAssignment assignment(
            PlatformRole role,
            PlatformRoleStatus status) {
        return new PlatformRoleAssignment(
                UUID.randomUUID(),
                IDENTITY_ID,
                role,
                status,
                CREATED_AT,
                status == PlatformRoleStatus.REVOKED ? CREATED_AT.plusSeconds(1) : null);
    }
}
