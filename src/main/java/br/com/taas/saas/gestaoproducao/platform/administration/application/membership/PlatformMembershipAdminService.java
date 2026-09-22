package br.com.taas.saas.gestaoproducao.platform.administration.application.membership;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.GrantPlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.RevokePlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.PlatformRoleAssignmentConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

@Service
public class PlatformMembershipAdminService {

    private final PlatformRoleRepository platformRoleRepository;
    private final MembershipRepository membershipRepository;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final AdministrativeAuditRepository administrativeAuditRepository;

    public PlatformMembershipAdminService(
            PlatformRoleRepository platformRoleRepository,
            MembershipRepository membershipRepository,
            PlatformAuthorizationService platformAuthorizationService,
            AdministrativeAuditRepository administrativeAuditRepository) {
        this.platformRoleRepository = Objects.requireNonNull(
                platformRoleRepository,
                "platformRoleRepository must not be null");
        this.membershipRepository = Objects.requireNonNull(
                membershipRepository,
                "membershipRepository must not be null");
        this.platformAuthorizationService = Objects.requireNonNull(
                platformAuthorizationService,
                "platformAuthorizationService must not be null");
        this.administrativeAuditRepository = Objects.requireNonNull(
                administrativeAuditRepository,
                "administrativeAuditRepository must not be null");
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        PlatformMembershipAdminValidationException.class
    })
    public PlatformRoleAssignment grantPlatformAdmin(GrantPlatformAdminCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireOwner(command.actorIdentityId(), AuditAction.PLATFORM_ADMIN_GRANTED, null,
                command.occurredAt());

        if (!platformRoleRepository.findActiveByIdentityId(command.targetIdentityId()).isEmpty()) {
            throw invalid(
                    command.actorIdentityId(),
                    null,
                    AuditAction.PLATFORM_ADMIN_GRANTED,
                    command.occurredAt(),
                    "identity already has an active platform role");
        }

        PlatformRoleAssignment assignment = new PlatformRoleAssignment(
                UUID.randomUUID(),
                command.targetIdentityId(),
                PlatformRole.PLATFORM_ADMIN,
                PlatformRoleStatus.ACTIVE,
                command.occurredAt(),
                null);
        PlatformRoleAssignment saved;
        try {
            saved = platformRoleRepository.save(assignment);
        } catch (PlatformRoleAssignmentConflictException exception) {
            throw invalid(
                    command.actorIdentityId(),
                    assignment.id(),
                    AuditAction.PLATFORM_ADMIN_GRANTED,
                    command.occurredAt(),
                    "platform administrator assignment conflicts with an existing role",
                    exception);
        }
        recordAudit(
                command.actorIdentityId(),
                saved.id(),
                AuditAction.PLATFORM_ADMIN_GRANTED,
                AuditResult.SUCCESS,
                command.occurredAt());
        return saved;
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        PlatformMembershipAdminValidationException.class
    })
    public PlatformRoleAssignment revokePlatformAdmin(RevokePlatformAdminCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireOwner(
                command.actorIdentityId(),
                AuditAction.PLATFORM_ADMIN_REVOKED,
                command.platformRoleAssignmentId(),
                command.occurredAt());

        PlatformRoleAssignment current = platformRoleRepository
                .findById(command.platformRoleAssignmentId())
                .orElseThrow(() -> invalid(
                        command.actorIdentityId(),
                        command.platformRoleAssignmentId(),
                        AuditAction.PLATFORM_ADMIN_REVOKED,
                        command.occurredAt(),
                        "platform role assignment was not found"));
        if (current.role() == PlatformRole.PLATFORM_OWNER) {
            throw invalid(
                    command.actorIdentityId(),
                    current.id(),
                    AuditAction.PLATFORM_ADMIN_REVOKED,
                    command.occurredAt(),
                    "platform owner cannot be revoked by this operation");
        }
        if (current.role() != PlatformRole.PLATFORM_ADMIN || !current.isActive()) {
            throw invalid(
                    command.actorIdentityId(),
                    current.id(),
                    AuditAction.PLATFORM_ADMIN_REVOKED,
                    command.occurredAt(),
                    "only an active platform administrator can be revoked");
        }

        PlatformRoleAssignment revoked = platformRoleRepository.save(
                current.revoke(command.occurredAt()));
        recordAudit(
                command.actorIdentityId(),
                revoked.id(),
                AuditAction.PLATFORM_ADMIN_REVOKED,
                AuditResult.SUCCESS,
                command.occurredAt());
        return revoked;
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        PlatformMembershipAdminValidationException.class
    })
    public Membership revokeMembership(RevokeMembershipCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requirePlatformAccess(
                command.actorIdentityId(),
                AuditAction.MEMBERSHIP_REVOKED,
                command.membershipId(),
                command.occurredAt());

        Membership current = membershipRepository.findById(command.membershipId())
                .orElseThrow(() -> invalid(
                        command.actorIdentityId(),
                        command.membershipId(),
                        AuditAction.MEMBERSHIP_REVOKED,
                        command.occurredAt(),
                        "membership was not found"));
        if (!MembershipRole.TENANT_USER.equals(current.role())) {
            throw invalid(
                    command.actorIdentityId(),
                    current.id(),
                    AuditAction.MEMBERSHIP_REVOKED,
                    command.occurredAt(),
                    "only TENANT_USER memberships can be revoked");
        }
        if (current.status() == MembershipStatus.REVOKED) {
            throw invalid(
                    command.actorIdentityId(),
                    current.id(),
                    AuditAction.MEMBERSHIP_REVOKED,
                    command.occurredAt(),
                    "membership is already revoked");
        }

        Membership revoked = membershipRepository.save(current.revoke(command.occurredAt()));
        recordAudit(
                command.actorIdentityId(),
                revoked.id(),
                AuditAction.MEMBERSHIP_REVOKED,
                AuditResult.SUCCESS,
                command.occurredAt());
        return revoked;
    }

    @Transactional(readOnly = true)
    public PlatformMembershipAdministrationView view(UUID actorIdentityId) {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        platformAuthorizationService.requirePlatformAccess(actorIdentityId);

        List<PlatformRoleAdministrationView> roles = platformRoleRepository.findAll().stream()
                .sorted(Comparator.comparing(PlatformRoleAssignment::createdAt)
                        .thenComparing(PlatformRoleAssignment::id))
                .map(assignment -> new PlatformRoleAdministrationView(
                        assignment.id(),
                        assignment.identityId(),
                        assignment.role(),
                        assignment.status(),
                        assignment.createdAt(),
                        assignment.revokedAt()))
                .toList();
        List<MembershipAdministrationView> memberships = membershipRepository.findAll().stream()
                .sorted(Comparator.comparing(Membership::createdAt)
                        .thenComparing(Membership::id))
                .map(membership -> new MembershipAdministrationView(
                        membership.id(),
                        membership.identityId(),
                        membership.tenantId(),
                        membership.status(),
                        membership.role(),
                        membership.createdAt(),
                        membership.revokedAt()))
                .toList();
        return new PlatformMembershipAdministrationView(roles, memberships);
    }

    private void requireOwner(
            UUID actorIdentityId,
            AuditAction action,
            UUID targetId,
            Instant occurredAt) {
        try {
            platformAuthorizationService.requireOwner(actorIdentityId);
        } catch (PlatformAuthorizationDeniedException exception) {
            recordAudit(actorIdentityId, targetId, action, AuditResult.DENIED, occurredAt);
            throw exception;
        }
    }

    private void requirePlatformAccess(
            UUID actorIdentityId,
            AuditAction action,
            UUID targetId,
            Instant occurredAt) {
        try {
            platformAuthorizationService.requirePlatformAccess(actorIdentityId);
        } catch (PlatformAuthorizationDeniedException exception) {
            recordAudit(actorIdentityId, targetId, action, AuditResult.DENIED, occurredAt);
            throw exception;
        }
    }

    private PlatformMembershipAdminValidationException invalid(
            UUID actorIdentityId,
            UUID targetId,
            AuditAction action,
            Instant occurredAt,
            String message) {
        recordAudit(actorIdentityId, targetId, action, AuditResult.FAILED, occurredAt);
        return new PlatformMembershipAdminValidationException(message);
    }

    private PlatformMembershipAdminValidationException invalid(
            UUID actorIdentityId,
            UUID targetId,
            AuditAction action,
            Instant occurredAt,
            String message,
            Throwable cause) {
        recordAudit(actorIdentityId, targetId, action, AuditResult.FAILED, occurredAt);
        return new PlatformMembershipAdminValidationException(message, cause);
    }

    private void recordAudit(
            UUID actorIdentityId,
            UUID targetId,
            AuditAction action,
            AuditResult result,
            Instant occurredAt) {
        administrativeAuditRepository.save(new AuditEvent(
                UUID.randomUUID(),
                actorIdentityId,
                action,
                targetType(action),
                targetId,
                result,
                occurredAt,
                AuditMetadata.empty()));
    }

    private AuditTargetType targetType(AuditAction action) {
        return action == AuditAction.MEMBERSHIP_REVOKED
                ? AuditTargetType.MEMBERSHIP
                : AuditTargetType.PLATFORM_ROLE;
    }
}
