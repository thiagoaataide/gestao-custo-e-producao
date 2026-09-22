package br.com.taas.saas.gestaoproducao.platform.access.application;

public final class PlatformAuthorizationDeniedException extends RuntimeException {

    public PlatformAuthorizationDeniedException() {
        super("Platform operation is not authorized");
    }
}
