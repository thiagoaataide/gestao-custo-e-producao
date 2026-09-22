package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

import java.util.UUID;

public final class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(UUID tenantId) {
        super("Tenant was not found: " + tenantId);
    }
}
