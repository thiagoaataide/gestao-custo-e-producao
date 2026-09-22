package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.application.port.out.AuthenticatedIdentityPort;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.MembershipRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentityStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Membership;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;

@Service
public class InvitationAcceptanceService {

    private final AuthenticatedIdentityPort authenticatedIdentityPort;
    private final InvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final ExternalIdentityRepository externalIdentityRepository;
    private final MembershipRepository membershipRepository;
    private final AdministrativeAuditRepository administrativeAuditRepository;

    public InvitationAcceptanceService(
            AuthenticatedIdentityPort authenticatedIdentityPort,
            InvitationRepository invitationRepository,
            TenantRepository tenantRepository,
            ExternalIdentityRepository externalIdentityRepository,
            MembershipRepository membershipRepository,
            AdministrativeAuditRepository administrativeAuditRepository) {
        this.authenticatedIdentityPort = Objects.requireNonNull(
                authenticatedIdentityPort,
                "authenticatedIdentityPort must not be null");
        this.invitationRepository = Objects.requireNonNull(
                invitationRepository,
                "invitationRepository must not be null");
        this.tenantRepository = Objects.requireNonNull(
                tenantRepository,
                "tenantRepository must not be null");
        this.externalIdentityRepository = Objects.requireNonNull(
                externalIdentityRepository,
                "externalIdentityRepository must not be null");
        this.membershipRepository = Objects.requireNonNull(
                membershipRepository,
                "membershipRepository must not be null");
        this.administrativeAuditRepository = Objects.requireNonNull(
                administrativeAuditRepository,
                "administrativeAuditRepository must not be null");
    }

    @Transactional(noRollbackFor = InvitationAcceptanceException.class)
    public InvitationAcceptanceResult acceptInvitation(AcceptInvitationCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<AuthenticatedIdentityProfile> profile = authenticatedIdentityPort
                .loadVerifiedProfile(command.accessTokenContext());
        if (profile.isEmpty() || !profile.orElseThrow().emailVerified()) {
            throw deny(null, null, command.occurredAt());
        }

        AuthenticatedIdentityProfile authenticated = profile.orElseThrow();
        Invitation invitation = findPendingInvitation(command);
        if (invitation == null) {
            throw deny(existingIdentityId(authenticated), null, command.occurredAt());
        }

        Tenant tenant = tenantRepository.findById(invitation.tenantId()).orElse(null);
        if (tenant == null || !tenant.isAvailable()) {
            throw deny(existingIdentityId(authenticated), invitation.id(), command.occurredAt());
        }

        ExternalIdentity identity = existingIdentity(authenticated);
        if (!authenticated.email().equals(invitation.email())) {
            throw deny(identityId(identity), invitation.id(), command.occurredAt());
        }
        if (invitation.identityId() != null
                && (identity == null || !invitation.identityId().equals(identity.id()))) {
            throw deny(identityId(identity), invitation.id(), command.occurredAt());
        }
        if (identity == null) {
            identity = externalIdentityRepository.save(new ExternalIdentity(
                    UUID.randomUUID(),
                    authenticated.subject(),
                    ExternalIdentityStatus.ACTIVE,
                    command.occurredAt()));
        }
        if (!identity.isActive()) {
            throw deny(identity.id(), invitation.id(), command.occurredAt());
        }

        Membership membership = existingMembershipForTenant(identity.id(), invitation.tenantId());
        boolean membershipCreated = false;
        if (membership == null) {
            List<Membership> activeMemberships = membershipRepository
                    .findActiveByIdentityId(identity.id());
            if (!activeMemberships.isEmpty()) {
                throw deny(identity.id(), invitation.id(), command.occurredAt());
            }
            membership = new Membership(
                    UUID.randomUUID(),
                    identity.id(),
                    invitation.tenantId(),
                    MembershipStatus.ACTIVE,
                    MembershipRole.TENANT_USER,
                    command.occurredAt(),
                    null);
            try {
                membership = membershipRepository.save(membership);
                membershipCreated = true;
            } catch (DataIntegrityViolationException exception) {
                throw new InvitationAcceptanceException(exception);
            }
        }

        Invitation acceptedInvitation = invitationRepository.save(
                invitation.accept(identity.id(), command.occurredAt()));
        if (membershipCreated) {
            recordAudit(
                    identity.id(),
                    membership.id(),
                    AuditTargetType.MEMBERSHIP,
                    AuditAction.MEMBERSHIP_CREATED,
                    command.occurredAt());
        }
        recordAudit(
                identity.id(),
                acceptedInvitation.id(),
                AuditTargetType.INVITATION,
                AuditAction.INVITATION_ACCEPTED,
                command.occurredAt());
        return new InvitationAcceptanceResult(acceptedInvitation, membership);
    }

    private Invitation findPendingInvitation(AcceptInvitationCommand command) {
        try {
            return invitationRepository
                    .findPendingByTokenForUpdate(command.token(), command.occurredAt())
                    .orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ExternalIdentity existingIdentity(AuthenticatedIdentityProfile profile) {
        return externalIdentityRepository
                .findByProviderAndExternalSubject(profile.subject())
                .orElse(null);
    }

    private UUID existingIdentityId(AuthenticatedIdentityProfile profile) {
        return identityId(existingIdentity(profile));
    }

    private UUID identityId(ExternalIdentity identity) {
        return identity == null ? null : identity.id();
    }

    private Membership existingMembershipForTenant(UUID identityId, UUID tenantId) {
        return membershipRepository.findActiveByIdentityId(identityId).stream()
                .filter(membership -> membership.tenantId().equals(tenantId))
                .findFirst()
                .orElse(null);
    }

    private InvitationAcceptanceException deny(
            UUID actorIdentityId,
            UUID invitationId,
            Instant occurredAt) {
        recordAudit(
                actorIdentityId,
                invitationId,
                AuditTargetType.INVITATION,
                AuditAction.INVITATION_ACCEPTED,
                AuditResult.DENIED,
                occurredAt);
        return new InvitationAcceptanceException();
    }

    private void recordAudit(
            UUID actorIdentityId,
            UUID targetId,
            AuditTargetType targetType,
            AuditAction action,
            Instant occurredAt) {
        recordAudit(actorIdentityId, targetId, targetType, action, AuditResult.SUCCESS, occurredAt);
    }

    private void recordAudit(
            UUID actorIdentityId,
            UUID targetId,
            AuditTargetType targetType,
            AuditAction action,
            AuditResult result,
            Instant occurredAt) {
        administrativeAuditRepository.save(new AuditEvent(
                UUID.randomUUID(),
                actorIdentityId,
                action,
                targetType,
                targetId,
                result,
                occurredAt,
                AuditMetadata.empty()));
    }
}
