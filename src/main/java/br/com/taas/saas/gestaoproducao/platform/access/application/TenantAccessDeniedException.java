package br.com.taas.saas.gestaoproducao.platform.access.application;

import java.util.Objects;

/**
 * Signals that an authenticated subject cannot execute a tenant-scoped use case.
 */
public final class TenantAccessDeniedException extends RuntimeException {

    private final AccessDecisionType decisionType;

    public TenantAccessDeniedException(AccessDecisionType decisionType) {
        super("Tenant-scoped operation is not authorized");
        this.decisionType = Objects.requireNonNull(decisionType, "decisionType must not be null");
    }

    public AccessDecisionType decisionType() {
        return decisionType;
    }
}
