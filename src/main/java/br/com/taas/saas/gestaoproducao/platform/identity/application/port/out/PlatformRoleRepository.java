package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;

public interface PlatformRoleRepository {

    List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId);

    Optional<PlatformRoleAssignment> findActiveOwner();

    PlatformRoleAssignment save(PlatformRoleAssignment assignment);
}
