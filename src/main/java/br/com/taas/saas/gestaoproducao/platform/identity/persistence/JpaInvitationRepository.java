package br.com.taas.saas.gestaoproducao.platform.identity.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.InvitationConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.InvitationJpaEntity;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.InvitationJpaRepository;

@Repository
public class JpaInvitationRepository implements InvitationRepository {

    private static final String PENDING_UNIQUE_INDEX =
            "invitation_one_pending_tenant_email_uq";

    private final InvitationJpaRepository repository;

    public JpaInvitationRepository(InvitationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Invitation> findById(UUID invitationId) {
        return repository.findById(invitationId).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<Invitation> findPendingByToken(String token, Instant now) {
        String tokenDigest = InvitationTokenDigest.fromToken(token).value();
        return repository
                .findByTokenDigestAndStatusAndExpiresAtAfter(
                        tokenDigest,
                        InvitationStatus.PENDING,
                        now)
                .map(entity -> entity.toDomain());
    }

    @Override
    public Optional<Invitation> findPendingByTenantAndEmail(
            UUID tenantId,
            String email,
            Instant now) {
        return repository
                .findByTenantIdAndEmailAndStatusAndExpiresAtAfter(
                        tenantId,
                        br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail
                                .from(email)
                                .value(),
                        InvitationStatus.PENDING,
                        now)
                .map(entity -> entity.toDomain());
    }

    @Override
    public Invitation save(Invitation invitation) {
        try {
            return repository
                    .saveAndFlush(InvitationJpaEntity.fromDomain(invitation))
                    .toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (!isPendingUniquenessViolation(exception)) {
                throw exception;
            }
            throw new InvitationConflictException(
                    "a pending invitation already exists for this tenant and email",
                    exception);
        }
    }

    private boolean isPendingUniquenessViolation(
            DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(PENDING_UNIQUE_INDEX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
