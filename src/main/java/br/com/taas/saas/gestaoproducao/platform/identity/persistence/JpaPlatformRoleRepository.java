package br.com.taas.saas.gestaoproducao.platform.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.PlatformRoleAssignmentConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.PlatformRoleJpaEntity;
import br.com.taas.saas.gestaoproducao.platform.identity.persistence.jpa.PlatformRoleJpaRepository;

@Repository
public class JpaPlatformRoleRepository implements PlatformRoleRepository {

    private static final String OWNER_UNIQUE_INDEX =
            "platform_role_one_active_owner_uq";
    private static final String IDENTITY_ROLE_UNIQUE_INDEX =
            "platform_role_one_active_per_identity_uq";

    private final PlatformRoleJpaRepository repository;

    public JpaPlatformRoleRepository(PlatformRoleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PlatformRoleAssignment> findActiveByIdentityId(UUID identityId) {
        return repository
                .findByIdentityIdAndStatus(identityId, PlatformRoleStatus.ACTIVE)
                .stream()
                .map(entity -> entity.toDomain())
                .toList();
    }

    @Override
    public Optional<PlatformRoleAssignment> findActiveOwner() {
        return repository
                .findByRoleAndStatus(PlatformRole.PLATFORM_OWNER, PlatformRoleStatus.ACTIVE)
                .map(entity -> entity.toDomain());
    }

    @Override
    public PlatformRoleAssignment save(PlatformRoleAssignment assignment) {
        try {
            return repository
                    .saveAndFlush(PlatformRoleJpaEntity.fromDomain(assignment))
                    .toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (!isPlatformRoleUniquenessViolation(exception)) {
                throw exception;
            }
            throw new PlatformRoleAssignmentConflictException(
                    "active platform role assignment conflicts with an existing assignment",
                    exception);
        }
    }

    private boolean isPlatformRoleUniquenessViolation(
            DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains(OWNER_UNIQUE_INDEX)
                            || message.contains(IDENTITY_ROLE_UNIQUE_INDEX))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
