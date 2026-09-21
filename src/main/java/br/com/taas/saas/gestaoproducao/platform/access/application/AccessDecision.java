package br.com.taas.saas.gestaoproducao.platform.access.application;

import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantAccessContext;

public record AccessDecision(
        AccessDecisionType type,
        ExternalSubject subject,
        TenantAccessContext tenantContext) {

    public AccessDecision {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        if (type == AccessDecisionType.TENANT_ACCESS && tenantContext == null) {
            throw new IllegalArgumentException("tenant access requires a tenant context");
        }
        if (type != AccessDecisionType.TENANT_ACCESS && tenantContext != null) {
            throw new IllegalArgumentException("only tenant access may have a tenant context");
        }
    }

    public static AccessDecision tenantAccess(
            ExternalSubject subject,
            TenantAccessContext tenantContext) {
        return new AccessDecision(AccessDecisionType.TENANT_ACCESS, subject, tenantContext);
    }

    public static AccessDecision platformAccess(ExternalSubject subject) {
        return new AccessDecision(AccessDecisionType.PLATFORM_ACCESS, subject, null);
    }

    public static AccessDecision notProvisioned(ExternalSubject subject) {
        return new AccessDecision(AccessDecisionType.NOT_PROVISIONED, subject, null);
    }

    public static AccessDecision ambiguousMembership(ExternalSubject subject) {
        return new AccessDecision(AccessDecisionType.AMBIGUOUS_MEMBERSHIP, subject, null);
    }
}
