package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.time.Instant;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;

/** Safe invitation metadata for platform administration; no token material is exposed. */
public record InvitationAdministrationView(
        UUID id,
        UUID tenantId,
        String email,
        MembershipRole role,
        InvitationStatus status,
        UUID identityId,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        UUID createdBy,
        Instant createdAt) {
}
