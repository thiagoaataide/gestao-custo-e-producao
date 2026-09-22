package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CreateInvitationCommand(
        UUID actorIdentityId,
        UUID tenantId,
        String email,
        Instant occurredAt) {

    public CreateInvitationCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
