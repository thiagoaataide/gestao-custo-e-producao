package br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;

public interface InvitationJpaRepository
        extends JpaRepository<InvitationJpaEntity, UUID> {

    Optional<InvitationJpaEntity> findByTokenDigestAndStatusAndExpiresAtAfter(
            String tokenDigest,
            InvitationStatus status,
            Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from InvitationJpaEntity invitation
            where invitation.tokenDigest = :tokenDigest
              and invitation.status = :status
              and invitation.expiresAt > :now
            """)
    Optional<InvitationJpaEntity> findPendingByTokenForUpdate(
            @Param("tokenDigest") String tokenDigest,
            @Param("status") InvitationStatus status,
            @Param("now") Instant now);

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
