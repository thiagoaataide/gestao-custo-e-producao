package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CreateTenantCommand(
        UUID actorIdentityId,
        String name,
        Instant occurredAt) {

    public CreateTenantCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
