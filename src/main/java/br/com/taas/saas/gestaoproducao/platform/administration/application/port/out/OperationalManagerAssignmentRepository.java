package br.com.taas.saas.gestaoproducao.platform.administration.application.port.out;

import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignment;

public interface OperationalManagerAssignmentRepository {

    Optional<OperationalManagerAssignment> findById(UUID assignmentId);

    Optional<OperationalManagerAssignment> findActiveByMembershipId(UUID membershipId);

    OperationalManagerAssignment save(OperationalManagerAssignment assignment);
}
