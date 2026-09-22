package br.com.taas.saas.gestaoproducao.platform.access.application;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;

@Service
public final class PlatformAuthorizationService {

    private final PlatformRoleRepository platformRoleRepository;

    public PlatformAuthorizationService(PlatformRoleRepository platformRoleRepository) {
        this.platformRoleRepository = Objects.requireNonNull(
                platformRoleRepository,
                "platformRoleRepository must not be null");
    }

    public Optional<PlatformRoleAssignment> resolveActiveRole(UUID identityId) {
        Objects.requireNonNull(identityId, "identityId must not be null");

        return platformRoleRepository.findActiveByIdentityId(identityId).stream()
                .filter(Objects::nonNull)
                .filter(PlatformRoleAssignment::isActive)
                .min(Comparator.comparingInt(assignment ->
                        assignment.role().isOwner() ? 0 : 1));
    }

    public boolean hasPlatformAccess(UUID identityId) {
        return resolveActiveRole(identityId).isPresent();
    }

    public boolean canManagePlatformRoles(UUID identityId) {
        return resolveActiveRole(identityId)
                .map(PlatformRoleAssignment::role)
                .map(PlatformRole::isOwner)
                .orElse(false);
    }

    public void requirePlatformAccess(UUID identityId) {
        if (!hasPlatformAccess(identityId)) {
            throw new PlatformAuthorizationDeniedException();
        }
    }

    public void requireOwner(UUID identityId) {
        if (!canManagePlatformRoles(identityId)) {
            throw new PlatformAuthorizationDeniedException();
        }
    }
}
