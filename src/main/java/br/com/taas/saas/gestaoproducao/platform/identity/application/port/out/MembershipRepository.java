package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;

public interface MembershipRepository {

    default Optional<Membership> findById(UUID membershipId) {
        throw new UnsupportedOperationException("membership persistence is not available");
    }

    default List<Membership> findByIdentityId(UUID identityId) {
        throw new UnsupportedOperationException("membership persistence is not available");
    }

    List<Membership> findActiveByIdentityId(UUID identityId);

    default Membership save(Membership membership) {
        throw new UnsupportedOperationException("membership persistence is not available");
    }
}
