package br.com.taas.saas.gestaoproducao.platform.administration.infrastructure.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.OperationalManagerAssignmentRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignment;
import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignmentStatus;

@Repository
public class OperationalManagerAssignmentPersistenceAdapter
        implements OperationalManagerAssignmentRepository {

    private final OperationalManagerAssignmentJpaRepository repository;

    public OperationalManagerAssignmentPersistenceAdapter(
            OperationalManagerAssignmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<OperationalManagerAssignment> findById(UUID assignmentId) {
        return repository.findById(assignmentId);
    }

    @Override
    public Optional<OperationalManagerAssignment> findActiveByMembershipId(UUID membershipId) {
        return repository.findByMembershipIdAndStatus(
                membershipId, OperationalManagerAssignmentStatus.ACTIVE);
    }

    @Override
    public List<OperationalManagerAssignment> findAll() {
        return repository.findAll();
    }

    @Override
    public OperationalManagerAssignment save(OperationalManagerAssignment assignment) {
        return repository.saveAndFlush(assignment);
    }
}
