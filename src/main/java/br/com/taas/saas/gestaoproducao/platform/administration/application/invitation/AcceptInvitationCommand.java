package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.time.Instant;
import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;

public record AcceptInvitationCommand(
        String token,
        AccessTokenContext accessTokenContext,
        Instant occurredAt) {

    public AcceptInvitationCommand {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(accessTokenContext, "accessTokenContext must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
