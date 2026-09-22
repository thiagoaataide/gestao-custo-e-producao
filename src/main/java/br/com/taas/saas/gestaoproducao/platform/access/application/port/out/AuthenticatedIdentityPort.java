package br.com.taas.saas.gestaoproducao.platform.access.application.port.out;

import java.util.Optional;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;

/**
 * Boundary for obtaining trusted profile data from the already authenticated
 * identity provider.
 */
public interface AuthenticatedIdentityPort {

    /**
     * Returns a verified profile or an empty result when the identity cannot
     * be safely used for provisioning.
     */
    Optional<AuthenticatedIdentityProfile> loadVerifiedProfile(
            AccessTokenContext accessTokenContext);
}
