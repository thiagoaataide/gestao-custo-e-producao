package br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment;

import java.util.UUID;

/** Read-only contract published for operational bounded contexts. */
public interface OperationalManagerAuthorizationQuery {

    boolean isActiveOperationalManager(UUID identityId, UUID tenantId);
}
