package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;

public record InvitationLinkResult(Invitation invitation, String link) {

    public InvitationLinkResult {
        Objects.requireNonNull(invitation, "invitation must not be null");
        Objects.requireNonNull(link, "link must not be null");
    }
}
