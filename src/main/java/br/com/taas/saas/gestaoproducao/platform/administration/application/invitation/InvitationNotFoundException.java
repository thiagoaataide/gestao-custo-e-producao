package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.util.UUID;

public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException(UUID invitationId) {
        super("invitation not found: " + invitationId);
    }
}
