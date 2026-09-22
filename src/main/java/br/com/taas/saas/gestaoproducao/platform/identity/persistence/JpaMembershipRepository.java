package br.com.taas.saas.gestaoproducao.platform.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.MembershipJpaEntity;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.MembershipJpaRepository;

@Repository
public class JpaMembershipRepository implements MembershipRepository {

    private final MembershipJpaRepository repository;

    public JpaMembershipRepository(MembershipJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Membership> findActiveByIdentityId(UUID identityId) {
        return repository
                .findByIdentityIdAndStatus(identityId, MembershipStatus.ACTIVE)
                .stream()
                .map(entity -> entity.toDomain())
                .toList();
    }

    @Override
    public Optional<Membership> findById(UUID membershipId) {
        return repository.findById(membershipId).map(entity -> entity.toDomain());
    }

    @Override
    public List<Membership> findByIdentityId(UUID identityId) {
        return repository.findByIdentityId(identityId).stream()
                .map(entity -> entity.toDomain())
                .toList();
    }

    @Override
    public Membership save(Membership membership) {
        return repository
                .saveAndFlush(MembershipJpaEntity.fromDomain(membership))
                .toDomain();
    }
}
