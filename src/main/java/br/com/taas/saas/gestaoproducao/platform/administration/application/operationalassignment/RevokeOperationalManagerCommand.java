package br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RevokeOperationalManagerCommand(
        UUID actorIdentityId,
        UUID assignmentId,
        Instant occurredAt) {

    public RevokeOperationalManagerCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
