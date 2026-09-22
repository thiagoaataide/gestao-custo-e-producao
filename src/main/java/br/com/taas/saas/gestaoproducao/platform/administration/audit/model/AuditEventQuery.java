package br.com.taas.saas.gestaoproducao.platform.administration.audit.model;

import java.time.Instant;
import java.util.UUID;

public record AuditEventQuery(
        UUID actorIdentityId,
        AuditAction action,
        AuditTargetType targetType,
        UUID targetId,
        AuditResult result,
        Instant occurredFrom,
        Instant occurredUntil,
        int page,
        int size) {

    private static final int MAX_PAGE_SIZE = 100;

    public AuditEventQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (occurredFrom != null
                && occurredUntil != null
                && !occurredUntil.isAfter(occurredFrom)) {
            throw new IllegalArgumentException("occurredUntil must be after occurredFrom");
        }
    }

    public static AuditEventQuery firstPage(int size) {
        return new AuditEventQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                size);
    }
}
