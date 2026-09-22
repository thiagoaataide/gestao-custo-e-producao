package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

public class InvitationCommandValidationException extends RuntimeException {

    public InvitationCommandValidationException(String message) {
        super(message);
    }

    public InvitationCommandValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
