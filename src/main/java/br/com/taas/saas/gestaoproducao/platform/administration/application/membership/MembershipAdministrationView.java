package br.com.taas.saas.gestaoproducao.platform.administration.application.membership;

import java.time.Instant;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;

public record MembershipAdministrationView(
        UUID id,
        UUID identityId,
        UUID tenantId,
        MembershipStatus status,
        MembershipRole role,
        Instant createdAt,
        Instant revokedAt) {
}
