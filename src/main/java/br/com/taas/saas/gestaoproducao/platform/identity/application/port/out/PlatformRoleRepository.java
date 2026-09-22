package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;

public interface PlatformRoleRepository {

    List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId);

    Optional<PlatformRoleAssignment> findActiveOwner();

    default Optional<PlatformRoleAssignment> findById(UUID assignmentId) {
        throw new UnsupportedOperationException("platform role persistence is not available");
    }

    default List<PlatformRoleAssignment> findAll() {
        throw new UnsupportedOperationException("platform role persistence is not available");
    }

    PlatformRoleAssignment save(PlatformRoleAssignment assignment);
}
