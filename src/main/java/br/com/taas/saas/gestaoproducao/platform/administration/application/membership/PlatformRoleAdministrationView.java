package br.com.taas.saas.gestaoproducao.platform.administration.application.membership;

import java.time.Instant;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

public record PlatformRoleAdministrationView(
        UUID id,
        UUID identityId,
        PlatformRole role,
        PlatformRoleStatus status,
        Instant createdAt,
        Instant revokedAt) {
}
