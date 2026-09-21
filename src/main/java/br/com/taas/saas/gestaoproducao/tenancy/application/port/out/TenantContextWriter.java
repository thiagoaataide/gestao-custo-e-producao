package br.com.taas.saas.gestaoproducao.tenancy.application.port.out;

import java.sql.Connection;

import br.com.taas.saas.gestaoproducao.tenancy.model.TenantId;

/**
 * Writes the trusted tenant context into the current database transaction.
 */
public interface TenantContextWriter {

    void apply(TenantId tenantId, Connection connection);
}
