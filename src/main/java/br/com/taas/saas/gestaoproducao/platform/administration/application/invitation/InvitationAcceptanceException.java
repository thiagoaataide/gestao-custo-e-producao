package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

public final class InvitationAcceptanceException extends RuntimeException {

    public InvitationAcceptanceException() {
        super("invitation cannot be accepted");
    }

    public InvitationAcceptanceException(Throwable cause) {
        super("invitation cannot be accepted", cause);
    }
}
