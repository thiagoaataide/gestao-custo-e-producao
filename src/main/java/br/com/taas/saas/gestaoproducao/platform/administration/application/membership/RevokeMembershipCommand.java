package br.com.taas.saas.gestaoproducao.platform.administration.application.membership;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RevokeMembershipCommand(
        UUID actorIdentityId,
        UUID membershipId,
        Instant occurredAt) {

    public RevokeMembershipCommand {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
