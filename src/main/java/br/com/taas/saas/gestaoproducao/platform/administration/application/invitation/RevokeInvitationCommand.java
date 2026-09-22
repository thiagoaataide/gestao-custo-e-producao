package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RevokeInvitationCommand(
        UUID actorIdentityId,
        UUID invitationId,
        Instant occurredAt) {

    public RevokeInvitationCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(invitationId, "invitationId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
