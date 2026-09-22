package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

public final class TenantLifecycleValidationException extends RuntimeException {

    public TenantLifecycleValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
