package br.com.taas.saas.gestaoproducao.platform.administration.persistence.jpa;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;

@Entity
@Table(name = "audit_event", schema = "platform")
public class AuditEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_identity_id")
    private UUID actorIdentityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private AuditTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditResult result;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;

    protected AuditEventJpaEntity() {
    }

    private AuditEventJpaEntity(
            UUID id,
            UUID actorIdentityId,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            AuditResult result,
            Instant occurredAt,
            Map<String, String> metadata) {
        this.id = id;
        this.actorIdentityId = actorIdentityId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.occurredAt = occurredAt;
        this.metadata = metadata;
    }

    public static AuditEventJpaEntity fromDomain(AuditEvent event) {
        return new AuditEventJpaEntity(
                event.id(),
                event.actorIdentityId(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.result(),
                event.occurredAt(),
                event.metadata().values());
    }

    public AuditEvent toDomain() {
        return new AuditEvent(
                id,
                actorIdentityId,
                action,
                targetType,
                targetId,
                result,
                occurredAt,
                AuditMetadata.of(metadata));
    }
}
