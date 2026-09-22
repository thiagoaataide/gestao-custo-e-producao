package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChangeTenantStatusCommand(
        UUID actorIdentityId,
        UUID tenantId,
        TenantLifecycleAction action,
        Instant occurredAt) {

    public ChangeTenantStatusCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
