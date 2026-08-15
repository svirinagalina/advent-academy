package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.RoleApprovalWorkflowPort;
import com.adventacademy.userservice.domain.exception.DuplicateActiveRoleRequestException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.domain.repository.RoleRequestRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleRequestServiceTest {

    @Mock private RoleRequestRepository roleRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditTrailPort auditTrailPort;
    @Mock private RoleApprovalWorkflowPort workflowPort;

    private RoleRequestService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);

    private UUID userId;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        service = new RoleRequestService(
                roleRequestRepository, userRepository, roleRepository, auditTrailPort, workflowPort, clock);
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
    }

    @Test
    void submitCreatesPendingRequestAndRecordsAudit() {
        User user = new User();
        user.setId(userId);
        Role role = new Role();
        role.setId(roleId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRequestRepository.existsActiveByUserIdAndRequestedRoleId(userId, roleId)).thenReturn(false);
        when(workflowPort.start(any(UUID.class), eq(userId), eq(roleId))).thenReturn(9001L);
        when(roleRequestRepository.save(any(RoleRequest.class))).thenAnswer(inv -> {
            RoleRequest request = inv.getArgument(0);
            UUID id = request.getId() != null ? request.getId() : UUID.randomUUID();
            return RoleRequest.restore(
                    id,
                    request.getUserId(),
                    request.getRequestedRoleId(),
                    request.getStatus(),
                    request.getReviewerId(),
                    request.getDecisionReason(),
                    request.getProcessInstanceKey(),
                    request.getCreatedAt(),
                    request.getReviewedAt(),
                    request.getUpdatedAt());
        });

        var response = service.submit(userId, roleId, "127.0.0.1", "JUnit");

        assertThat(response.status()).isEqualTo(RoleRequestStatus.PENDING);
        assertThat(response.id()).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.requestedRoleId()).isEqualTo(roleId);
        verify(workflowPort).start(response.id(), userId, roleId);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(auditCaptor.capture());
        AuditEvent event = auditCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.ROLE_REQUEST_SUBMITTED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(userId);
        assertThat(event.getTargetUserId()).isEqualTo(userId);
        assertThat(event.getMetadata())
                .containsEntry("roleRequestId", response.id().toString())
                .containsEntry("requestedRoleId", roleId.toString());
    }

    @Test
    void submitRejectsDuplicateActiveRequest() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(new Role()));
        when(roleRequestRepository.existsActiveByUserIdAndRequestedRoleId(userId, roleId)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(userId, roleId, "127.0.0.1", "JUnit"))
                .isInstanceOf(DuplicateActiveRoleRequestException.class);

        verify(roleRequestRepository, never()).save(any(RoleRequest.class));
        verify(auditTrailPort, never()).record(any(AuditEvent.class));
        verify(workflowPort, never()).start(any(), any(), any());
    }
}
