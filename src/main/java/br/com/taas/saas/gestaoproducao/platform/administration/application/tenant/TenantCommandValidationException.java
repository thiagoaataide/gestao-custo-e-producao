package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

public final class TenantCommandValidationException extends RuntimeException {

    public TenantCommandValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
