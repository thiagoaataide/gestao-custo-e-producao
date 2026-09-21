package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ExternalIdentity {

    private final UUID id;
    private final ExternalSubject subject;
    private final ExternalIdentityStatus status;
    private final Instant createdAt;

    public ExternalIdentity(
            UUID id,
            ExternalSubject subject,
            ExternalIdentityStatus status,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID id() {
        return id;
    }

    public ExternalSubject subject() {
        return subject;
    }

    public ExternalIdentityStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == ExternalIdentityStatus.ACTIVE;
    }
}
