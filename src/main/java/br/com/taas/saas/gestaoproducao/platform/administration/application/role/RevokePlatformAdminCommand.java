package br.com.taas.saas.gestaoproducao.platform.administration.application.role;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RevokePlatformAdminCommand(
        UUID actorIdentityId,
        UUID platformRoleAssignmentId,
        Instant occurredAt) {

    public RevokePlatformAdminCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(
                platformRoleAssignmentId,
                "platformRoleAssignmentId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
