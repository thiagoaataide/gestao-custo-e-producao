package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;

public interface InvitationRepository {

    Optional<Invitation> findById(UUID invitationId);

    Optional<Invitation> findPendingByToken(String token, Instant now);

    /**
     * Loads a pending invitation while serializing concurrent acceptances of
     * the same token. Adapters without a locking primitive retain the same
     * contract and rely on the database uniqueness constraints.
     */
    default Optional<Invitation> findPendingByTokenForUpdate(String token, Instant now) {
        return findPendingByToken(token, now);
    }

    Optional<Invitation> findPendingByTenantAndEmail(
            UUID tenantId,
            String email,
            Instant now);

    Optional<Invitation> findPendingByTenantAndEmailIncludingExpired(
            UUID tenantId,
            String email);

    Invitation save(Invitation invitation);
}
