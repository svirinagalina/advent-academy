package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.RoleApprovalWorkflowPort;
import com.adventacademy.userservice.domain.exception.DuplicateActiveRoleRequestException;
import com.adventacademy.userservice.domain.exception.InvalidRoleRequestStateException;
import com.adventacademy.userservice.domain.exception.SelfApprovalNotAllowedException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.domain.model.RoleRequestDecision;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.domain.repository.RoleRequestRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
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
    @Mock private AssignRoleService assignRoleService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RoleRequestService self;

    private RoleRequestService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime earlier = LocalDateTime.parse("2026-07-22T09:00:00");

    private UUID userId;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        service = new RoleRequestService(roleRequestRepository, userRepository, roleRepository,
                auditTrailPort, workflowPort, assignRoleService, eventPublisher, self, clock);
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
    }

    @Test
    void submitCreatesPendingRequestRecordsAuditAndPublishesSubmittedEventWithoutStartingTheWorkflowInline() {
        User user = new User();
        user.setId(userId);
        Role role = new Role();
        role.setId(roleId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRequestRepository.existsActiveByUserIdAndRequestedRoleId(userId, roleId)).thenReturn(false);
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

        // The process is started later, from onRoleRequestSubmitted after commit —
        // not synchronously inside submit()'s own transaction.
        verify(workflowPort, never()).start(any(), any(), any());
        verify(eventPublisher).publishEvent(new RoleRequestSubmittedEvent(response.id(), userId, roleId));

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
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onRoleRequestSubmittedStartsWorkflowAndDelegatesAttachThroughSelf() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest pending = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.PENDING,
                null, null, null, earlier, null, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(pending));
        when(workflowPort.start(roleRequestId, userId, roleId)).thenReturn(9001L);

        service.onRoleRequestSubmitted(new RoleRequestSubmittedEvent(roleRequestId, userId, roleId));

        verify(workflowPort).start(roleRequestId, userId, roleId);
        // Routed through the injected self-proxy, not called directly (this. would be AOP
        // self-invocation and silently skip attachProcessInstance's own @Transactional).
        verify(self).attachProcessInstance(roleRequestId, 9001L);
        verify(auditTrailPort, never()).record(any(AuditEvent.class));
    }

    @Test
    void attachProcessInstanceLoadsMutatesAndSaves() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest pending = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.PENDING,
                null, null, null, earlier, null, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(pending));

        service.attachProcessInstance(roleRequestId, 9001L);

        assertThat(pending.getProcessInstanceKey()).isEqualTo(9001L);
        verify(roleRequestRepository).save(pending);
    }

    @Test
    void attachProcessInstanceSkipsIfAlreadyAttached() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest alreadyAttached = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.PENDING,
                null, null, 42L, earlier, null, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(alreadyAttached));

        service.attachProcessInstance(roleRequestId, 9001L);

        assertThat(alreadyAttached.getProcessInstanceKey()).isEqualTo(42L);
        verify(roleRequestRepository, never()).save(any());
    }

    @Test
    void onRoleRequestSubmittedSkipsStartIfAlreadyAttached() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest alreadyStarted = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.PENDING,
                null, null, 42L, earlier, null, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(alreadyStarted));

        service.onRoleRequestSubmitted(new RoleRequestSubmittedEvent(roleRequestId, userId, roleId));

        verify(workflowPort, never()).start(any(), any(), any());
        verify(roleRequestRepository, never()).save(any());
    }

    @Test
    void onRoleRequestSubmittedAuditsFailureAndLeavesRequestPendingForRetry() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest pending = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.PENDING,
                null, null, null, earlier, null, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(pending));
        when(workflowPort.start(roleRequestId, userId, roleId))
                .thenThrow(new RuntimeException("zeebe unavailable"));

        service.onRoleRequestSubmitted(new RoleRequestSubmittedEvent(roleRequestId, userId, roleId));

        verify(roleRequestRepository, never()).save(any());
        assertThat(pending.getStatus()).isEqualTo(RoleRequestStatus.PENDING);
        assertThat(pending.getProcessInstanceKey()).isNull();

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(auditCaptor.capture());
        AuditEvent event = auditCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.ROLE_REQUEST_WORKFLOW_START_FAILED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILED);
    }

    @Test
    void approveGrantsRoleThenSavesThenAuditsInThatOrder() {
        UUID roleRequestId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        RoleRequest pending = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.PENDING,
                null, null, 42L, earlier, null, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(pending));

        service.approve(roleRequestId, userId, roleId, reviewerId);

        InOrder order = inOrder(assignRoleService, roleRequestRepository, auditTrailPort);
        order.verify(assignRoleService).assignRoleToUser(userId, roleId, null, "camunda-worker");
        order.verify(roleRequestRepository).save(pending);
        order.verify(auditTrailPort).record(any(AuditEvent.class));

        assertThat(pending.getStatus()).isEqualTo(RoleRequestStatus.APPROVED);
        assertThat(pending.getReviewerId()).isEqualTo(reviewerId);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getEventType()).isEqualTo(AuditEventType.ROLE_REQUEST_APPROVED);
    }

    @Test
    void approveOnAlreadyApprovedRequestIsANoOp() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest approved = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.APPROVED,
                UUID.randomUUID(), null, 42L, earlier, earlier, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(approved));

        service.approve(roleRequestId, userId, roleId, UUID.randomUUID());

        verify(assignRoleService, never()).assignRoleToUser(any(), any(), any(), any());
        verify(roleRequestRepository, never()).save(any());
        verify(auditTrailPort, never()).record(any());
    }

    @Test
    void approveOnWrongTerminalStateThrowsWithoutEverGrantingTheRole() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest rejected = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.REJECTED,
                UUID.randomUUID(), "not needed", 42L, earlier, earlier, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.approve(roleRequestId, userId, roleId, UUID.randomUUID()))
                .isInstanceOf(InvalidRoleRequestStateException.class);

        verify(assignRoleService, never()).assignRoleToUser(any(), any(), any(), any());
        verify(roleRequestRepository, never()).save(any());
        verify(auditTrailPort, never()).record(any());
    }

    @Test
    void submitDecisionRejectsSelfApproval() {
        UUID roleRequestId = UUID.randomUUID();
        RoleRequest pending = RoleRequest.restore(roleRequestId, userId, roleId, RoleRequestStatus.PENDING,
                null, null, 42L, earlier, null, earlier);
        when(roleRequestRepository.findById(roleRequestId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.submitDecision(roleRequestId, userId, RoleRequestDecision.APPROVE, null))
                .isInstanceOf(SelfApprovalNotAllowedException.class);

        verify(workflowPort, never()).submitDecision(anyLong(), any(), any(), any());
    }
}
