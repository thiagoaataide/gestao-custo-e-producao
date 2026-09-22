package br.com.taas.saas.gestaoproducao.platform.administration.application.membership;

public class PlatformMembershipAdminValidationException extends RuntimeException {

    public PlatformMembershipAdminValidationException(String message) {
        super(message);
    }

    public PlatformMembershipAdminValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
