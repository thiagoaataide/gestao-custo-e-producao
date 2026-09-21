package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.util.List;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;

public interface MembershipRepository {

    List<Membership> findActiveByIdentityId(UUID identityId);
}
