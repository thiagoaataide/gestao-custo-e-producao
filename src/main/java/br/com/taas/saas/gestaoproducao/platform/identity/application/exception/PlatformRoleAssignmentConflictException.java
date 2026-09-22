package br.com.taas.saas.gestaoproducao.platform.identity.application.exception;

public class PlatformRoleAssignmentConflictException extends RuntimeException {

    public PlatformRoleAssignmentConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
