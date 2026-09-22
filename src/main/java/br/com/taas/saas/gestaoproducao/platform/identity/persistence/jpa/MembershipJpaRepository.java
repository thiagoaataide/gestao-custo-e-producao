package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;

public interface MembershipJpaRepository extends JpaRepository<MembershipJpaEntity, UUID> {

    List<MembershipJpaEntity> findByIdentityId(UUID identityId);

    List<MembershipJpaEntity> findByIdentityIdAndStatus(
            UUID identityId,
            MembershipStatus status);
}
