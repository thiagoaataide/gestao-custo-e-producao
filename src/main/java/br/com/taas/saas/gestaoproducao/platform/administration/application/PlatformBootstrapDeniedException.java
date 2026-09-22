package br.com.taas.saas.gestaoproducao.platform.administration.application;

public final class PlatformBootstrapDeniedException extends RuntimeException {

    public PlatformBootstrapDeniedException() {
        super("Platform owner bootstrap is not authorized");
    }
}
