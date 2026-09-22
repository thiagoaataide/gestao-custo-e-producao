package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;

public record InvitationAcceptanceResult(
        Invitation invitation,
        Membership membership) {

    public InvitationAcceptanceResult {
        Objects.requireNonNull(invitation, "invitation must not be null");
        Objects.requireNonNull(membership, "membership must not be null");
    }
}
