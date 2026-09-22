package br.com.taas.saas.gestaoproducao.platform.administration.application.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@Service
public class TenantProvisioningCommandService {

    private final TenantRepository tenantRepository;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final AdministrativeAuditRepository administrativeAuditRepository;

    public TenantProvisioningCommandService(
            TenantRepository tenantRepository,
            PlatformAuthorizationService platformAuthorizationService,
            AdministrativeAuditRepository administrativeAuditRepository) {
        this.tenantRepository = Objects.requireNonNull(
                tenantRepository,
                "tenantRepository must not be null");
        this.platformAuthorizationService = Objects.requireNonNull(
                platformAuthorizationService,
                "platformAuthorizationService must not be null");
        this.administrativeAuditRepository = Objects.requireNonNull(
                administrativeAuditRepository,
                "administrativeAuditRepository must not be null");
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        TenantCommandValidationException.class
    })
    public Tenant createTenant(CreateTenantCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        try {
            platformAuthorizationService.requirePlatformAccess(command.actorIdentityId());
        } catch (PlatformAuthorizationDeniedException exception) {
            recordAudit(
                    command.actorIdentityId(),
                    null,
                    AuditAction.TENANT_CREATED,
                    AuditResult.DENIED,
                    command.occurredAt());
            throw exception;
        }

        Tenant tenant;
        try {
            tenant = new Tenant(
                    UUID.randomUUID(),
                    command.name(),
                    TenantStatus.ACTIVE,
                    command.occurredAt());
        } catch (IllegalArgumentException exception) {
            recordAudit(
                    command.actorIdentityId(),
                    null,
                    AuditAction.TENANT_CREATED,
                    AuditResult.FAILED,
                    command.occurredAt());
            throw new TenantCommandValidationException("tenant data is invalid", exception);
        }
        Tenant savedTenant = tenantRepository.save(tenant);
        recordAudit(
                command.actorIdentityId(),
                savedTenant.id(),
                AuditAction.TENANT_CREATED,
                AuditResult.SUCCESS,
                command.occurredAt());
        return savedTenant;
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        TenantNotFoundException.class,
        TenantLifecycleValidationException.class
    })
    public Tenant changeTenantStatus(ChangeTenantStatusCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        AuditAction action = auditAction(command.action());
        try {
            platformAuthorizationService.requireOwner(command.actorIdentityId());
        } catch (PlatformAuthorizationDeniedException exception) {
            recordAudit(
                    command.actorIdentityId(),
                    command.tenantId(),
                    action,
                    AuditResult.DENIED,
                    command.occurredAt());
            throw exception;
        }

        Tenant currentTenant;
        try {
            currentTenant = tenantRepository.findById(command.tenantId())
                    .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));
        } catch (TenantNotFoundException exception) {
            recordAudit(
                    command.actorIdentityId(),
                    command.tenantId(),
                    action,
                    AuditResult.FAILED,
                    command.occurredAt());
            throw exception;
        }

        Tenant changedTenant;
        try {
            changedTenant = switch (command.action()) {
                case SUSPEND -> currentTenant.suspend();
                case REACTIVATE -> currentTenant.reactivate();
                case CLOSE -> currentTenant.close();
            };
        } catch (IllegalStateException exception) {
            recordAudit(
                    command.actorIdentityId(),
                    command.tenantId(),
                    action,
                    AuditResult.FAILED,
                    command.occurredAt());
            throw new TenantLifecycleValidationException(
                    "tenant lifecycle transition is invalid",
                    exception);
        }

        Tenant savedTenant = tenantRepository.save(changedTenant);
        recordAudit(
                command.actorIdentityId(),
                savedTenant.id(),
                action,
                AuditResult.SUCCESS,
                command.occurredAt());
        return savedTenant;
    }

    private AuditAction auditAction(TenantLifecycleAction action) {
        return switch (action) {
            case SUSPEND -> AuditAction.TENANT_SUSPENDED;
            case REACTIVATE -> AuditAction.TENANT_REACTIVATED;
            case CLOSE -> AuditAction.TENANT_CLOSED;
        };
    }

    private void recordAudit(
            UUID actorIdentityId,
            UUID tenantId,
            AuditAction action,
            AuditResult result,
            Instant occurredAt) {
        administrativeAuditRepository.save(new AuditEvent(
                UUID.randomUUID(),
                actorIdentityId,
                action,
                AuditTargetType.TENANT,
                tenantId,
                result,
                occurredAt,
                AuditMetadata.empty()));
    }
}
