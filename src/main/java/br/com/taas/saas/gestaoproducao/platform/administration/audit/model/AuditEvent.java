package br.com.taas.saas.gestaoproducao.platform.administration.audit.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuditEvent {

    private final UUID id;
    private final UUID actorIdentityId;
    private final AuditAction action;
    private final AuditTargetType targetType;
    private final UUID targetId;
    private final AuditResult result;
    private final Instant occurredAt;
    private final AuditMetadata metadata;

    public AuditEvent(
            UUID id,
            UUID actorIdentityId,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            AuditResult result,
            Instant occurredAt,
            AuditMetadata metadata) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.actorIdentityId = actorIdentityId;
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.targetId = targetId;
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
    }

    public UUID id() {
        return id;
    }

    public UUID actorIdentityId() {
        return actorIdentityId;
    }

    public AuditAction action() {
        return action;
    }

    public AuditTargetType targetType() {
        return targetType;
    }

    public UUID targetId() {
        return targetId;
    }

    public AuditResult result() {
        return result;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public AuditMetadata metadata() {
        return metadata;
    }
}
