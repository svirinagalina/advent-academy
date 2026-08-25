package com.adventacademy.userservice.domain.model;

/**
 * The admin's input when reviewing a pending {@link RoleRequest}. Distinct from
 * {@link RoleRequestStatus}: this is what the reviewer chooses, the status is the
 * resulting state once the Camunda process has actually applied it.
 */
public enum RoleRequestDecision {
    APPROVE,
    REJECT
}
