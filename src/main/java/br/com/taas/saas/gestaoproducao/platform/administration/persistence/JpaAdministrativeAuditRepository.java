package br.com.taas.saas.gestaoproducao.platform.administration.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.persistence.jpa.AuditEventJpaEntity;
import br.com.taas.saas.gestaoproducao.platform.administration.persistence.jpa.AuditEventJpaRepository;

@Repository
public class JpaAdministrativeAuditRepository implements AdministrativeAuditRepository {

    private final AuditEventJpaRepository repository;

    public JpaAdministrativeAuditRepository(AuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditEvent save(AuditEvent event) {
        return repository
                .saveAndFlush(AuditEventJpaEntity.fromDomain(event))
                .toDomain();
    }

    @Override
    public AuditEventPage findPage(AuditEventQuery query) {
        var page = repository.findAll(
                specificationFor(query),
                PageRequest.of(
                        query.page(),
                        query.size(),
                        Sort.by(
                                Sort.Order.desc("occurredAt"),
                                Sort.Order.desc("id"))));

        return new AuditEventPage(
                page.getContent().stream()
                        .map(entity -> entity.toDomain())
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    private Specification<AuditEventJpaEntity> specificationFor(AuditEventQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (query.actorIdentityId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.<UUID>get("actorIdentityId"),
                        query.actorIdentityId()));
            }
            if (query.action() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.<AuditAction>get("action"),
                        query.action()));
            }
            if (query.targetType() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.<AuditTargetType>get("targetType"),
                        query.targetType()));
            }
            if (query.targetId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.<UUID>get("targetId"),
                        query.targetId()));
            }
            if (query.result() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.<AuditResult>get("result"),
                        query.result()));
            }
            if (query.occurredFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.<Instant>get("occurredAt"),
                        query.occurredFrom()));
            }
            if (query.occurredUntil() != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.<Instant>get("occurredAt"),
                        query.occurredUntil()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
