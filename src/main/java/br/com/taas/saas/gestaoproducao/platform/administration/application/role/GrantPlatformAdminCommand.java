package br.com.taas.saas.gestaoproducao.platform.administration.application.role;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GrantPlatformAdminCommand(
        UUID actorIdentityId,
        UUID targetIdentityId,
        Instant occurredAt) {

    public GrantPlatformAdminCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(targetIdentityId, "targetIdentityId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
