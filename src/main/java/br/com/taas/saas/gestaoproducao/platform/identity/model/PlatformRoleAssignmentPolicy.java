package br.com.taas.saas.gestaoproducao.platform.identity.model;

import java.util.Collection;
import java.util.Objects;

public final class PlatformRoleAssignmentPolicy {

    private PlatformRoleAssignmentPolicy() {
    }

    public static void ensureSingleActiveOwner(
            Collection<PlatformRoleAssignment> assignments) {
        Objects.requireNonNull(assignments, "assignments must not be null");

        long activeOwners = assignments.stream()
                .filter(Objects::nonNull)
                .filter(PlatformRoleAssignment::isActive)
                .filter(assignment -> assignment.role().isOwner())
                .count();
        if (activeOwners > 1) {
            throw new IllegalArgumentException(
                    "only one active platform owner is allowed");
        }
    }

    public static boolean canManagePlatformRoles(PlatformRoleAssignment assignment) {
        return assignment != null
                && assignment.isActive()
                && assignment.role().isOwner();
    }
}
