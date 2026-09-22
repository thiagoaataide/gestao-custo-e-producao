package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;

@Service
public class TenantProvisioningQueryService {

    private final TenantRepository tenantRepository;
    private final PlatformAuthorizationService platformAuthorizationService;

    public TenantProvisioningQueryService(
            TenantRepository tenantRepository,
            PlatformAuthorizationService platformAuthorizationService) {
        this.tenantRepository = Objects.requireNonNull(
                tenantRepository,
                "tenantRepository must not be null");
        this.platformAuthorizationService = Objects.requireNonNull(
                platformAuthorizationService,
                "platformAuthorizationService must not be null");
    }

    @Transactional(readOnly = true)
    public Tenant findTenant(UUID actorIdentityId, UUID tenantId) {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        platformAuthorizationService.requirePlatformAccess(actorIdentityId);

        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }
}
