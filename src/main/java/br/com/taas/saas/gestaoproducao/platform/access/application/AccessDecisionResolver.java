package br.com.taas.saas.gestaoproducao.platform.access.application;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantAccessContext;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantId;

@Service
public final class AccessDecisionResolver {

    private final ExternalIdentityRepository externalIdentityRepository;
    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final PlatformAuthorizationService platformAuthorizationService;

    @Autowired
    public AccessDecisionResolver(
            ExternalIdentityRepository externalIdentityRepository,
            MembershipRepository membershipRepository,
            TenantRepository tenantRepository,
            PlatformAuthorizationService platformAuthorizationService) {
        this.externalIdentityRepository = Objects.requireNonNull(
                externalIdentityRepository,
                "externalIdentityRepository must not be null");
        this.membershipRepository = Objects.requireNonNull(
                membershipRepository,
                "membershipRepository must not be null");
        this.tenantRepository = Objects.requireNonNull(
                tenantRepository,
                "tenantRepository must not be null");
        this.platformAuthorizationService = Objects.requireNonNull(
                platformAuthorizationService,
                "platformAuthorizationService must not be null");
    }

    public AccessDecision resolve(ExternalSubject subject) {
        Objects.requireNonNull(subject, "subject must not be null");

        return externalIdentityRepository
                .findByProviderAndExternalSubject(subject)
                .filter(ExternalIdentity::isActive)
                .map(identity -> resolveForIdentity(subject, identity))
                .orElseGet(() -> AccessDecision.notProvisioned(subject));
    }

    private AccessDecision resolveForIdentity(
            ExternalSubject subject,
            ExternalIdentity identity) {
        if (platformAuthorizationService.hasPlatformAccess(identity.id())) {
            return AccessDecision.platformAccess(subject);
        }

        List<Membership> activeMemberships = membershipRepository
                .findActiveByIdentityId(identity.id())
                .stream()
                .filter(Membership::isActive)
                .toList();

        if (activeMemberships.isEmpty()) {
            return AccessDecision.notProvisioned(subject);
        }
        if (activeMemberships.size() > 1) {
            return AccessDecision.ambiguousMembership(subject);
        }

        Membership membership = activeMemberships.getFirst();

        return tenantRepository
                .findById(membership.tenantId())
                .filter(Tenant::isAvailable)
                .map(tenant -> AccessDecision.tenantAccess(
                        subject,
                        new TenantAccessContext(
                                subject,
                                identity.id(),
                                new TenantId(tenant.id()),
                                membership.role())))
                .orElseGet(() -> AccessDecision.notProvisioned(subject));
    }
}
