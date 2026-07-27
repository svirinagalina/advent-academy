package com.adventacademy.userservice.domain.model;

import com.adventacademy.userservice.domain.exception.InvalidRoleRequestStateException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleRequestTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-07-22T10:00:00");
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.parse("2026-07-22T11:00:00");

    @Test
    void createStartsPending() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        RoleRequest request = RoleRequest.create(userId, roleId, CREATED_AT);

        assertThat(request.getId()).isNull();
        assertThat(request.getUserId()).isEqualTo(userId);
        assertThat(request.getRequestedRoleId()).isEqualTo(roleId);
        assertThat(request.getStatus()).isEqualTo(RoleRequestStatus.PENDING);
        assertThat(request.getReviewerId()).isNull();
        assertThat(request.getDecisionReason()).isNull();
        assertThat(request.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(request.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void approveFinalizesPendingRequest() {
        RoleRequest request = RoleRequest.create(UUID.randomUUID(), UUID.randomUUID(), CREATED_AT);
        UUID reviewerId = UUID.randomUUID();

        request.approve(reviewerId, REVIEWED_AT);

        assertThat(request.getStatus()).isEqualTo(RoleRequestStatus.APPROVED);
        assertThat(request.getReviewerId()).isEqualTo(reviewerId);
        assertThat(request.getDecisionReason()).isNull();
        assertThat(request.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(request.getUpdatedAt()).isEqualTo(REVIEWED_AT);
    }

    @Test
    void rejectFinalizesPendingRequestWithReason() {
        RoleRequest request = RoleRequest.create(UUID.randomUUID(), UUID.randomUUID(), CREATED_AT);
        UUID reviewerId = UUID.randomUUID();

        request.reject(reviewerId, "Not needed", REVIEWED_AT);

        assertThat(request.getStatus()).isEqualTo(RoleRequestStatus.REJECTED);
        assertThat(request.getReviewerId()).isEqualTo(reviewerId);
        assertThat(request.getDecisionReason()).isEqualTo("Not needed");
        assertThat(request.getReviewedAt()).isEqualTo(REVIEWED_AT);
    }

    @Test
    void markFailedFinalizesPendingRequest() {
        RoleRequest request = RoleRequest.create(UUID.randomUUID(), UUID.randomUUID(), CREATED_AT);

        request.markFailed("Workflow failed", REVIEWED_AT);

        assertThat(request.getStatus()).isEqualTo(RoleRequestStatus.FAILED);
        assertThat(request.getDecisionReason()).isEqualTo("Workflow failed");
        assertThat(request.getReviewedAt()).isEqualTo(REVIEWED_AT);
    }

    @Test
    void finalRequestCannotBeApprovedAgain() {
        RoleRequest request = RoleRequest.create(UUID.randomUUID(), UUID.randomUUID(), CREATED_AT);
        request.approve(UUID.randomUUID(), REVIEWED_AT);

        assertThatThrownBy(() -> request.approve(UUID.randomUUID(), REVIEWED_AT.plusMinutes(1)))
                .isInstanceOf(InvalidRoleRequestStateException.class);
    }

    @Test
    void finalRequestCannotBeRejectedAgain() {
        RoleRequest request = RoleRequest.create(UUID.randomUUID(), UUID.randomUUID(), CREATED_AT);
        request.reject(UUID.randomUUID(), "Rejected", REVIEWED_AT);

        assertThatThrownBy(() -> request.reject(UUID.randomUUID(), "Again", REVIEWED_AT.plusMinutes(1)))
                .isInstanceOf(InvalidRoleRequestStateException.class);
    }
}
