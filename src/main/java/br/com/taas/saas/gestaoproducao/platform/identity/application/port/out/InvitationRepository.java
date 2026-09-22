package br.com.taas.saas.gestaoproducao.platform.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;

public interface InvitationRepository {

    Optional<Invitation> findById(UUID invitationId);

    Optional<Invitation> findPendingByToken(String token, Instant now);

    Optional<Invitation> findPendingByTenantAndEmail(
            UUID tenantId,
            String email,
            Instant now);

    Optional<Invitation> findPendingByTenantAndEmailIncludingExpired(
            UUID tenantId,
            String email);

    Invitation save(Invitation invitation);
}
