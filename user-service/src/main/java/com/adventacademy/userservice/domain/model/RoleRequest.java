package com.adventacademy.userservice.domain.model;

import com.adventacademy.userservice.domain.exception.InvalidRoleRequestStateException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Business record of a user's request to receive a role.
 *
 * <p>The model stores the approval state independently from Camunda. Workflow
 * orchestration may drive the process later, but final state transitions remain
 * ordinary domain behavior guarded by this aggregate. A newly created request
 * has no id until it is persisted by the repository.</p>
 */
public class RoleRequest {

    private final UUID id;
    private final UUID userId;
    private final UUID requestedRoleId;
    private RoleRequestStatus status;
    private UUID reviewerId;
    private String decisionReason;
    private final LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;

    private RoleRequest(UUID id,
                        UUID userId,
                        UUID requestedRoleId,
                        RoleRequestStatus status,
                        UUID reviewerId,
                        String decisionReason,
                        LocalDateTime createdAt,
                        LocalDateTime reviewedAt,
                        LocalDateTime updatedAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.requestedRoleId = Objects.requireNonNull(requestedRoleId, "requestedRoleId");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewerId = reviewerId;
        this.decisionReason = decisionReason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.reviewedAt = reviewedAt;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static RoleRequest create(UUID userId, UUID requestedRoleId, LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        return new RoleRequest(
                null,
                userId,
                requestedRoleId,
                RoleRequestStatus.PENDING,
                null,
                null,
                now,
                null,
                now);
    }

    public static RoleRequest restore(UUID id,
                                      UUID userId,
                                      UUID requestedRoleId,
                                      RoleRequestStatus status,
                                      UUID reviewerId,
                                      String decisionReason,
                                      LocalDateTime createdAt,
                                      LocalDateTime reviewedAt,
                                      LocalDateTime updatedAt) {
        return new RoleRequest(id, userId, requestedRoleId, status, reviewerId, decisionReason,
                createdAt, reviewedAt, updatedAt);
    }

    public void approve(UUID reviewerId, LocalDateTime now) {
        requirePending();
        this.reviewerId = Objects.requireNonNull(reviewerId, "reviewerId");
        this.decisionReason = null;
        this.status = RoleRequestStatus.APPROVED;
        this.reviewedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void reject(UUID reviewerId, String decisionReason, LocalDateTime now) {
        requirePending();
        this.reviewerId = Objects.requireNonNull(reviewerId, "reviewerId");
        this.decisionReason = decisionReason;
        this.status = RoleRequestStatus.REJECTED;
        this.reviewedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void markFailed(String reason, LocalDateTime now) {
        requirePending();
        this.decisionReason = reason;
        this.status = RoleRequestStatus.FAILED;
        this.reviewedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    private void requirePending() {
        if (status != RoleRequestStatus.PENDING) {
            throw new InvalidRoleRequestStateException(
                    String.format("Role request %s is %s and cannot be changed", id, status));
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRequestedRoleId() {
        return requestedRoleId;
    }

    public RoleRequestStatus getStatus() {
        return status;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
