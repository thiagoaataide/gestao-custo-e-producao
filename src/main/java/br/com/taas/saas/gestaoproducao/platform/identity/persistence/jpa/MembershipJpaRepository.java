package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;

public interface MembershipJpaRepository extends Repository<MembershipJpaEntity, UUID> {

    List<MembershipJpaEntity> findByIdentityIdAndStatus(
            UUID identityId,
            MembershipStatus status);
}
