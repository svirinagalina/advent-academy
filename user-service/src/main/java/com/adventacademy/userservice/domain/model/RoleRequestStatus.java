package com.adventacademy.userservice.domain.model;

/**
 * Lifecycle state of a role approval request.
 *
 * <p>`PENDING` is the only active state. `APPROVED`, `REJECTED`, and `FAILED`
 * are final states and cannot be reviewed again.</p>
 */
public enum RoleRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    FAILED;

    public boolean isFinal() {
        return this == APPROVED || this == REJECTED || this == FAILED;
    }
}
