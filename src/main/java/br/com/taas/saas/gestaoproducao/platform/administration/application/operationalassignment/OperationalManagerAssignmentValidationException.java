package br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment;

public class OperationalManagerAssignmentValidationException extends RuntimeException {

    public OperationalManagerAssignmentValidationException(String message) {
        super(message);
    }

    public OperationalManagerAssignmentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
