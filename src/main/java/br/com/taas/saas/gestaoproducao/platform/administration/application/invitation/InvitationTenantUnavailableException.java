package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.util.UUID;

public class InvitationTenantUnavailableException extends RuntimeException {

    public InvitationTenantUnavailableException(UUID tenantId) {
        super("tenant is not available for invitations: " + tenantId);
    }
}
