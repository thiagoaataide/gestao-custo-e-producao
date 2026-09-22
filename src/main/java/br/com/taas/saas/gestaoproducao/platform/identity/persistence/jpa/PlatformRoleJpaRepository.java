package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

public interface PlatformRoleJpaRepository
        extends JpaRepository<PlatformRoleJpaEntity, UUID> {

    List<PlatformRoleJpaEntity> findByIdentityIdAndStatus(
            UUID identityId,
            PlatformRoleStatus status);

    Optional<PlatformRoleJpaEntity> findByRoleAndStatus(
            PlatformRole role,
            PlatformRoleStatus status);
}
