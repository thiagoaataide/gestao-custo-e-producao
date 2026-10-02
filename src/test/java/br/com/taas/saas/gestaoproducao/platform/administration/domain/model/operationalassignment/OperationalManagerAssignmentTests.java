package br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OperationalManagerAssignmentTests {

    private static final Instant ASSIGNED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID MEMBERSHIP_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void assignmentCanBeRevokedAndKeepsItsAuditFields() {
        OperationalManagerAssignment assignment = OperationalManagerAssignment.assign(
                ASSIGNMENT_ID, TENANT_ID, MEMBERSHIP_ID, ACTOR_ID, ASSIGNED_AT);
        UUID revokedBy = UUID.randomUUID();
        Instant revokedAt = ASSIGNED_AT.plusSeconds(60);

        assignment.revoke(revokedBy, revokedAt);

        assertThat(assignment.status()).isEqualTo(OperationalManagerAssignmentStatus.REVOKED);
        assertThat(assignment.assignedBy()).isEqualTo(ACTOR_ID);
        assertThat(assignment.assignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(assignment.revokedBy()).isEqualTo(revokedBy);
        assertThat(assignment.revokedAt()).isEqualTo(revokedAt);
        assertThat(assignment.isActive()).isFalse();
    }

    @Test
    void rejectsRepeatedRevocationAndRevocationBeforeAssignment() {
        OperationalManagerAssignment assignment = OperationalManagerAssignment.assign(
                ASSIGNMENT_ID, TENANT_ID, MEMBERSHIP_ID, ACTOR_ID, ASSIGNED_AT);

        assertThatThrownBy(() -> assignment.revoke(ACTOR_ID, ASSIGNED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assignment.revoke(ACTOR_ID, ASSIGNED_AT);
        assertThatThrownBy(() -> assignment.revoke(ACTOR_ID, ASSIGNED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
