package br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment;

import java.time.Instant;
import java.util.UUID;

public record OperationalManagerAssignmentView(
        UUID id,
        UUID membershipId,
        String status,
        Instant assignedAt) {

    public OperationalManagerAssignmentView {
        java.util.Objects.requireNonNull(id, "id must not be null");
        java.util.Objects.requireNonNull(membershipId, "membershipId must not be null");
        java.util.Objects.requireNonNull(status, "status must not be null");
        java.util.Objects.requireNonNull(assignedAt, "assignedAt must not be null");
    }
}
