package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;

public interface InvitationJpaRepository
        extends JpaRepository<InvitationJpaEntity, UUID> {

    Optional<InvitationJpaEntity> findByTokenDigestAndStatusAndExpiresAtAfter(
            String tokenDigest,
            InvitationStatus status,
            Instant now);

    Optional<InvitationJpaEntity> findByTenantIdAndEmailAndStatusAndExpiresAtAfter(
            UUID tenantId,
            String email,
            InvitationStatus status,
            Instant now);

    Optional<InvitationJpaEntity> findByTenantIdAndEmailAndStatus(
            UUID tenantId,
            String email,
            InvitationStatus status);
}
