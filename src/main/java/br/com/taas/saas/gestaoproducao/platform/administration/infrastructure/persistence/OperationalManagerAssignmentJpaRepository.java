package br.com.taas.saas.gestaoproducao.platform.administration.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignment;
import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignmentStatus;

public interface OperationalManagerAssignmentJpaRepository
        extends JpaRepository<OperationalManagerAssignment, UUID> {

    Optional<OperationalManagerAssignment> findByMembershipIdAndStatus(
            UUID membershipId,
            OperationalManagerAssignmentStatus status);
}
