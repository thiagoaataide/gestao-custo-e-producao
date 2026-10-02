package br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.OperationalManagerAssignmentRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;

@Service
public class OperationalManagerAssignmentService {

    private final OperationalManagerAssignmentRepository assignmentRepository;
    private final MembershipRepository membershipRepository;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final AdministrativeAuditRepository administrativeAuditRepository;

    public OperationalManagerAssignmentService(
            OperationalManagerAssignmentRepository assignmentRepository,
            MembershipRepository membershipRepository,
            PlatformAuthorizationService platformAuthorizationService,
            AdministrativeAuditRepository administrativeAuditRepository) {
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository);
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
        this.platformAuthorizationService = Objects.requireNonNull(platformAuthorizationService);
        this.administrativeAuditRepository = Objects.requireNonNull(administrativeAuditRepository);
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        OperationalManagerAssignmentValidationException.class
    })
    public OperationalManagerAssignment assign(AssignOperationalManagerCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID assignmentId = UUID.randomUUID();
        requirePlatformAccess(
                command.actorIdentityId(), assignmentId,
                AuditAction.OPERATIONAL_MANAGER_ASSIGNED, command.occurredAt());

        Membership membership = membershipRepository.findById(command.membershipId())
                .orElseThrow(() -> invalid(
                        command.actorIdentityId(), assignmentId,
                        AuditAction.OPERATIONAL_MANAGER_ASSIGNED, command.occurredAt(),
                        "membership was not found"));
        if (!membership.isActive()) {
            throw invalid(
                    command.actorIdentityId(), assignmentId,
                    AuditAction.OPERATIONAL_MANAGER_ASSIGNED, command.occurredAt(),
                    "only an active membership can be assigned");
        }
        if (assignmentRepository.findActiveByMembershipId(membership.id()).isPresent()) {
            throw invalid(
                    command.actorIdentityId(), assignmentId,
                    AuditAction.OPERATIONAL_MANAGER_ASSIGNED, command.occurredAt(),
                    "membership already has an active operational assignment");
        }

        OperationalManagerAssignment assignment = OperationalManagerAssignment.assign(
                assignmentId,
                membership.tenantId(),
                membership.id(),
                command.actorIdentityId(),
                command.occurredAt());
        OperationalManagerAssignment saved = assignmentRepository.save(assignment);
        recordAudit(command.actorIdentityId(), saved.id(),
                AuditAction.OPERATIONAL_MANAGER_ASSIGNED, AuditResult.SUCCESS, command.occurredAt());
        return saved;
    }

    @Transactional(noRollbackFor = {
        PlatformAuthorizationDeniedException.class,
        OperationalManagerAssignmentValidationException.class
    })
    public OperationalManagerAssignment revoke(RevokeOperationalManagerCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requirePlatformAccess(
                command.actorIdentityId(), command.assignmentId(),
                AuditAction.OPERATIONAL_MANAGER_REVOKED, command.occurredAt());

        OperationalManagerAssignment assignment = assignmentRepository.findById(command.assignmentId())
                .orElseThrow(() -> invalid(
                        command.actorIdentityId(), command.assignmentId(),
                        AuditAction.OPERATIONAL_MANAGER_REVOKED, command.occurredAt(),
                        "operational assignment was not found"));
        try {
            assignment.revoke(command.actorIdentityId(), command.occurredAt());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(
                    command.actorIdentityId(), assignment.id(),
                    AuditAction.OPERATIONAL_MANAGER_REVOKED, command.occurredAt(),
                    exception.getMessage(), exception);
        }

        OperationalManagerAssignment saved = assignmentRepository.save(assignment);
        recordAudit(command.actorIdentityId(), saved.id(),
                AuditAction.OPERATIONAL_MANAGER_REVOKED, AuditResult.SUCCESS, command.occurredAt());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OperationalManagerAssignmentView> list(UUID actorIdentityId) {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        platformAuthorizationService.requirePlatformAccess(actorIdentityId);
        return assignmentRepository.findAll().stream()
                .sorted(Comparator.comparing(OperationalManagerAssignment::assignedAt)
                        .thenComparing(OperationalManagerAssignment::id))
                .map(assignment -> new OperationalManagerAssignmentView(
                        assignment.id(), assignment.membershipId(), assignment.status().name(), assignment.assignedAt()))
                .toList();
    }

    private void requirePlatformAccess(
            UUID actorIdentityId, UUID targetId, AuditAction action, Instant occurredAt) {
        try {
            platformAuthorizationService.requirePlatformAccess(actorIdentityId);
        } catch (PlatformAuthorizationDeniedException exception) {
            recordAudit(actorIdentityId, targetId, action, AuditResult.DENIED, occurredAt);
            throw exception;
        }
    }

    private OperationalManagerAssignmentValidationException invalid(
            UUID actorIdentityId, UUID targetId, AuditAction action, Instant occurredAt, String message) {
        recordAudit(actorIdentityId, targetId, action, AuditResult.FAILED, occurredAt);
        return new OperationalManagerAssignmentValidationException(message);
    }

    private OperationalManagerAssignmentValidationException invalid(
            UUID actorIdentityId,
            UUID targetId,
            AuditAction action,
            Instant occurredAt,
            String message,
            Throwable cause) {
        recordAudit(actorIdentityId, targetId, action, AuditResult.FAILED, occurredAt);
        return new OperationalManagerAssignmentValidationException(message, cause);
    }

    private void recordAudit(
            UUID actorIdentityId, UUID targetId, AuditAction action, AuditResult result, Instant occurredAt) {
        administrativeAuditRepository.save(new AuditEvent(
                UUID.randomUUID(), actorIdentityId, action,
                AuditTargetType.OPERATIONAL_ASSIGNMENT, targetId, result,
                occurredAt, AuditMetadata.empty()));
    }
}
