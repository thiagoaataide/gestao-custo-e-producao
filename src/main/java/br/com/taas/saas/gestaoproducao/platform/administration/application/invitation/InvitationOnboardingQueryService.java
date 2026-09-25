package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;

/** Exposes only the invited address for a valid bearer invitation token. */
@Service
public class InvitationOnboardingQueryService {

    private final InvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;

    public InvitationOnboardingQueryService(
            InvitationRepository invitationRepository,
            TenantRepository tenantRepository) {
        this.invitationRepository = Objects.requireNonNull(invitationRepository);
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
    }

    @Transactional(readOnly = true)
    public Optional<String> invitedEmail(String token, Instant now) {
        if (token == null || token.isBlank() || now == null) {
            return Optional.empty();
        }
        try {
            return invitationRepository.findPendingByToken(token, now)
                    .filter(invitation -> invitation.isPendingAt(now))
                    .filter(invitation -> tenantRepository.findById(invitation.tenantId())
                            .filter(tenant -> tenant.isAvailable())
                            .isPresent())
                    .map(invitation -> invitation.email().value());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
