package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.config.InvitationLinkProperties;
import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.InvitationConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;

@Service
public class InvitationCommandService {

    private static final Duration INVITATION_LIFETIME = Duration.ofHours(24);

    private final InvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final AdministrativeAuditRepository administrativeAuditRepository;
    private final InvitationTokenGenerator tokenGenerator;
    private final InvitationLinkProperties linkProperties;

    public InvitationCommandService(
            InvitationRepository invitationRepository,
            TenantRepository tenantRepository,
            PlatformAuthorizationService platformAuthorizationService,
            AdministrativeAuditRepository administrativeAuditRepository,
            InvitationTokenGenerator tokenGenerator,
            InvitationLinkProperties linkProperties) {
        this.invitationRepository = Objects.requireNonNull(
                invitationRepository,
                "invitationRepository must not be null");
        this.tenantRepository = Objects.requireNonNull(
                tenantRepository,
                "tenantRepository must not be null");
        this.platformAuthorizationService = Objects.requireNonNull(
                platformAuthorizationService,
                "platformAuthorizationService must not be null");
        this.administrativeAuditRepository = Objects.requireNonNull(
                administrativeAuditRepository,
                "administrativeAuditRepository must not be null");
        this.tokenGenerator = Objects.requireNonNull(
                tokenGenerator,
                "tokenGenerator must not be null");
        this.linkProperties = Objects.requireNonNull(
                linkProperties,
                "linkProperties must not be null");
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        InvitationCommandValidationException.class,
        InvitationNotFoundException.class,
        InvitationTenantUnavailableException.class,
        InvitationConflictException.class
    })
    public InvitationLinkResult createInvitation(CreateInvitationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        authorize(command.actorIdentityId(), AuditAction.INVITATION_CREATED, null, command.occurredAt());
        availableTenant(command.tenantId(), command.actorIdentityId(), command.occurredAt());
        NormalizedEmail email = normalizedEmail(command.email(), command);
        expireExistingPendingIfNecessary(command.tenantId(), email, command);
        return createInvitationRecord(
                command.actorIdentityId(),
                command.tenantId(),
                email,
                command.occurredAt());
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        InvitationCommandValidationException.class,
        InvitationNotFoundException.class,
        InvitationTenantUnavailableException.class,
        InvitationConflictException.class
    })
    public InvitationLinkResult resendInvitation(ResendInvitationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        authorize(
                command.actorIdentityId(),
                AuditAction.INVITATION_CREATED,
                command.invitationId(),
                command.occurredAt());
        Invitation previous = invitationRepository.findById(command.invitationId())
                .orElseThrow(() -> notFound(command));
        availableTenant(previous.tenantId(), command.actorIdentityId(), command.occurredAt());

        if (previous.status() == InvitationStatus.PENDING) {
            if (previous.isPendingAt(command.occurredAt())) {
                Invitation revoked = invitationRepository.save(previous.revoke(command.occurredAt()));
                recordAudit(
                        command.actorIdentityId(),
                        revoked.id(),
                        AuditAction.INVITATION_REVOKED,
                        AuditResult.SUCCESS,
                        command.occurredAt());
            } else {
                Invitation expired = invitationRepository.save(previous.expire());
                recordAudit(
                        command.actorIdentityId(),
                        expired.id(),
                        AuditAction.INVITATION_EXPIRED,
                        AuditResult.SUCCESS,
                        command.occurredAt());
            }
        } else if (previous.status() != InvitationStatus.EXPIRED) {
            throw invalid(
                    command.actorIdentityId(),
                    previous.id(),
                    AuditAction.INVITATION_CREATED,
                    command.occurredAt(),
                    "only a pending or expired invitation can be resent");
        }

        return createInvitationRecord(
                command.actorIdentityId(),
                previous.tenantId(),
                previous.email(),
                command.occurredAt());
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        InvitationCommandValidationException.class,
        InvitationNotFoundException.class
    })
    public Invitation revokeInvitation(RevokeInvitationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        authorize(
                command.actorIdentityId(),
                AuditAction.INVITATION_REVOKED,
                command.invitationId(),
                command.occurredAt());
        Invitation current = invitationRepository.findById(command.invitationId())
                .orElseThrow(() -> notFound(command));
        if (current.status() != InvitationStatus.PENDING) {
            throw invalid(
                    command.actorIdentityId(),
                    current.id(),
                    AuditAction.INVITATION_REVOKED,
                    command.occurredAt(),
                    "only a pending invitation can be revoked");
        }
        if (!current.isPendingAt(command.occurredAt())) {
            Invitation expired = invitationRepository.save(current.expire());
            recordAudit(
                    command.actorIdentityId(),
                    expired.id(),
                    AuditAction.INVITATION_EXPIRED,
                    AuditResult.SUCCESS,
                    command.occurredAt());
            throw invalid(
                    command.actorIdentityId(),
                    expired.id(),
                    AuditAction.INVITATION_REVOKED,
                    command.occurredAt(),
                    "expired invitation cannot be revoked");
        }
        Invitation revoked = invitationRepository.save(current.revoke(command.occurredAt()));
        recordAudit(
                command.actorIdentityId(),
                revoked.id(),
                AuditAction.INVITATION_REVOKED,
                AuditResult.SUCCESS,
                command.occurredAt());
        return revoked;
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        InvitationCommandValidationException.class,
        InvitationNotFoundException.class
    })
    public Invitation expireInvitation(ExpireInvitationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        authorize(
                command.actorIdentityId(),
                AuditAction.INVITATION_EXPIRED,
                command.invitationId(),
                command.occurredAt());
        Invitation current = invitationRepository.findById(command.invitationId())
                .orElseThrow(() -> notFound(command));
        if (current.status() == InvitationStatus.EXPIRED) {
            return current;
        }
        if (current.status() != InvitationStatus.PENDING
                || current.isPendingAt(command.occurredAt())) {
            throw invalid(
                    command.actorIdentityId(),
                    current.id(),
                    AuditAction.INVITATION_EXPIRED,
                    command.occurredAt(),
                    "only an expired pending invitation can be expired");
        }
        Invitation expired = invitationRepository.save(current.expire());
        recordAudit(
                command.actorIdentityId(),
                expired.id(),
                AuditAction.INVITATION_EXPIRED,
                AuditResult.SUCCESS,
                command.occurredAt());
        return expired;
    }

    private InvitationLinkResult createInvitationRecord(
            UUID actorIdentityId,
            UUID tenantId,
            NormalizedEmail email,
            Instant occurredAt) {
        String rawToken = tokenGenerator.generate();
        Invitation invitation = new Invitation(
                UUID.randomUUID(),
                tenantId,
                email,
                MembershipRole.TENANT_USER,
                InvitationStatus.PENDING,
                InvitationTokenDigest.fromToken(rawToken),
                null,
                occurredAt.plus(INVITATION_LIFETIME),
                null,
                null,
                actorIdentityId,
                occurredAt);
        Invitation saved;
        try {
            saved = invitationRepository.save(invitation);
        } catch (InvitationConflictException exception) {
            recordAudit(
                    actorIdentityId,
                    null,
                    AuditAction.INVITATION_CREATED,
                    AuditResult.FAILED,
                    occurredAt);
            throw exception;
        }
        recordAudit(
                actorIdentityId,
                saved.id(),
                AuditAction.INVITATION_CREATED,
                AuditResult.SUCCESS,
                occurredAt);
        return new InvitationLinkResult(saved, linkProperties.linkFor(rawToken));
    }

    private void expireExistingPendingIfNecessary(
            UUID tenantId,
            NormalizedEmail email,
            CreateInvitationCommand command) {
        Optional<Invitation> existing = invitationRepository
                .findPendingByTenantAndEmailIncludingExpired(tenantId, email.value());
        if (existing.isEmpty()) {
            return;
        }
        Invitation current = existing.get();
        if (current.isPendingAt(command.occurredAt())) {
            recordAudit(
                    command.actorIdentityId(),
                    current.id(),
                    AuditAction.INVITATION_CREATED,
                    AuditResult.FAILED,
                    command.occurredAt());
            throw new InvitationConflictException(
                    "a pending invitation already exists for this tenant and email");
        }
        Invitation expired = invitationRepository.save(current.expire());
        recordAudit(
                command.actorIdentityId(),
                expired.id(),
                AuditAction.INVITATION_EXPIRED,
                AuditResult.SUCCESS,
                command.occurredAt());
    }

    private Tenant availableTenant(
            UUID tenantId,
            UUID actorIdentityId,
            Instant occurredAt) {
        try {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new InvitationCommandValidationException(
                            "tenant does not exist: " + tenantId));
            if (!tenant.isAvailable()) {
                recordAudit(
                        actorIdentityId,
                        null,
                        AuditAction.INVITATION_CREATED,
                        AuditResult.FAILED,
                        occurredAt);
                throw new InvitationTenantUnavailableException(tenantId);
            }
            return tenant;
        } catch (InvitationCommandValidationException exception) {
            recordAudit(
                    actorIdentityId,
                    null,
                    AuditAction.INVITATION_CREATED,
                    AuditResult.FAILED,
                    occurredAt);
            throw exception;
        }
    }

    private NormalizedEmail normalizedEmail(String email, CreateInvitationCommand command) {
        try {
            return NormalizedEmail.from(email);
        } catch (IllegalArgumentException exception) {
            recordAudit(
                    command.actorIdentityId(),
                    null,
                    AuditAction.INVITATION_CREATED,
                    AuditResult.FAILED,
                    command.occurredAt());
            throw new InvitationCommandValidationException("invitation email is invalid", exception);
        }
    }

    private void authorize(
            UUID actorIdentityId,
            AuditAction action,
            UUID invitationId,
            Instant occurredAt) {
        try {
            platformAuthorizationService.requirePlatformAccess(actorIdentityId);
        } catch (PlatformAuthorizationDeniedException exception) {
            recordAudit(actorIdentityId, invitationId, action, AuditResult.DENIED, occurredAt);
            throw exception;
        }
    }

    private InvitationNotFoundException notFound(ResendInvitationCommand command) {
        recordAudit(
                command.actorIdentityId(),
                command.invitationId(),
                AuditAction.INVITATION_CREATED,
                AuditResult.FAILED,
                command.occurredAt());
        return new InvitationNotFoundException(command.invitationId());
    }

    private InvitationNotFoundException notFound(RevokeInvitationCommand command) {
        recordAudit(
                command.actorIdentityId(),
                command.invitationId(),
                AuditAction.INVITATION_REVOKED,
                AuditResult.FAILED,
                command.occurredAt());
        return new InvitationNotFoundException(command.invitationId());
    }

    private InvitationNotFoundException notFound(ExpireInvitationCommand command) {
        recordAudit(
                command.actorIdentityId(),
                command.invitationId(),
                AuditAction.INVITATION_EXPIRED,
                AuditResult.FAILED,
                command.occurredAt());
        return new InvitationNotFoundException(command.invitationId());
    }

    private InvitationCommandValidationException invalid(
            UUID actorIdentityId,
            UUID invitationId,
            AuditAction action,
            Instant occurredAt,
            String message) {
        recordAudit(actorIdentityId, invitationId, action, AuditResult.FAILED, occurredAt);
        return new InvitationCommandValidationException(message);
    }

    private void recordAudit(
            UUID actorIdentityId,
            UUID invitationId,
            AuditAction action,
            AuditResult result,
            Instant occurredAt) {
        administrativeAuditRepository.save(new AuditEvent(
                UUID.randomUUID(),
                actorIdentityId,
                action,
                AuditTargetType.INVITATION,
                invitationId,
                result,
                occurredAt,
                AuditMetadata.empty()));
    }
}
