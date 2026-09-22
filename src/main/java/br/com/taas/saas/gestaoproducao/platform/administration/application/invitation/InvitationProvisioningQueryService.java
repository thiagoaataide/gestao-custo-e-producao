package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;

@Service
public class InvitationProvisioningQueryService {

    private final InvitationRepository invitationRepository;
    private final PlatformAuthorizationService platformAuthorizationService;

    public InvitationProvisioningQueryService(
            InvitationRepository invitationRepository,
            PlatformAuthorizationService platformAuthorizationService) {
        this.invitationRepository = Objects.requireNonNull(
                invitationRepository,
                "invitationRepository must not be null");
        this.platformAuthorizationService = Objects.requireNonNull(
                platformAuthorizationService,
                "platformAuthorizationService must not be null");
    }

    @Transactional(readOnly = true)
    public List<InvitationAdministrationView> listInvitations(UUID actorIdentityId) {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        platformAuthorizationService.requirePlatformAccess(actorIdentityId);

        return invitationRepository.findAll().stream()
                .sorted(Comparator.comparing(Invitation::createdAt).thenComparing(Invitation::id))
                .map(invitation -> new InvitationAdministrationView(
                        invitation.id(),
                        invitation.tenantId(),
                        invitation.email().value(),
                        invitation.role(),
                        invitation.status(),
                        invitation.identityId(),
                        invitation.expiresAt(),
                        invitation.acceptedAt(),
                        invitation.revokedAt(),
                        invitation.createdBy(),
                        invitation.createdAt()))
                .toList();
    }
}
