package br.com.taas.saas.gestaoproducao.platform.administration.application.audit;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;

/** Provides the authorized administrative audit read model to platform UI clients. */
@Service
public class AdministrativeAuditQueryService {

    private final AdministrativeAuditRepository administrativeAuditRepository;
    private final PlatformAuthorizationService platformAuthorizationService;

    public AdministrativeAuditQueryService(
            AdministrativeAuditRepository administrativeAuditRepository,
            PlatformAuthorizationService platformAuthorizationService) {
        this.administrativeAuditRepository = Objects.requireNonNull(
                administrativeAuditRepository,
                "administrativeAuditRepository must not be null");
        this.platformAuthorizationService = Objects.requireNonNull(
                platformAuthorizationService,
                "platformAuthorizationService must not be null");
    }

    @Transactional(readOnly = true)
    public AuditEventPage findPage(UUID actorIdentityId, AuditEventQuery query) {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(query, "query must not be null");
        platformAuthorizationService.requirePlatformAccess(actorIdentityId);
        return administrativeAuditRepository.findPage(query);
    }
}
