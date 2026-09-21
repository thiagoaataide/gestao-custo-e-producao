package br.com.taas.saas.gestaoproducao.tenancy.model;

import java.util.Objects;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;

public record TenantAccessContext(
        ExternalSubject externalSubject,
        UUID identityId,
        TenantId tenantId,
        MembershipRole role) {

    public TenantAccessContext {
        Objects.requireNonNull(externalSubject, "externalSubject must not be null");
        Objects.requireNonNull(identityId, "identityId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
