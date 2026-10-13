package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.dto.response.RoleRequestResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.RoleApprovalWorkflowPort;
import com.adventacademy.userservice.domain.exception.DuplicateActiveRoleRequestException;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.exception.RoleRequestNotFoundException;
import com.adventacademy.userservice.domain.exception.SelfApprovalNotAllowedException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.domain.model.RoleRequestDecision;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;
import com.adventacademy.userservice.domain.pagination.Sort;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.domain.repository.RoleRequestRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application use case for role approval requests.
 *
 * <p>Creates durable `RoleRequest` records, enforces the active duplicate rule,
 * starts and drives the {@code role-approval} Camunda process, records audit
 * events for every state transition, and exposes read models for user/admin
 * views. Camunda job workers call the {@code approve}/{@code markRejected}/
 * {@code markFailed} methods below instead of touching persistence directly.</p>
 */
@Service
public class RoleRequestService {

    private static final Logger logger = LoggerFactory.getLogger(RoleRequestService.class);

    private final RoleRequestRepository roleRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditTrailPort auditTrailPort;
    private final RoleApprovalWorkflowPort workflowPort;
    private final AssignRoleService assignRoleService;
    private final ApplicationEventPublisher eventPublisher;
    private final RoleRequestService self;
    private final Clock clock;

    /**
     * {@code self} is this same bean, injected lazily so Spring can hand back the
     * proxy without a construction-time cycle. It exists purely so
     * {@link #onRoleRequestSubmitted} can call {@link #attachProcessInstance}
     * <em>through the proxy</em>: calling it directly (plain {@code this.}) is
     * AOP self-invocation, which silently skips the {@code @Transactional}
     * advice — {@code REQUIRES_NEW} would never actually open a transaction, and
     * the attach would appear to succeed while writing nothing.
     */
    public RoleRequestService(RoleRequestRepository roleRequestRepository,
                              UserRepository userRepository,
                              RoleRepository roleRepository,
                              AuditTrailPort auditTrailPort,
                              RoleApprovalWorkflowPort workflowPort,
                              AssignRoleService assignRoleService,
                              ApplicationEventPublisher eventPublisher,
                              @Lazy RoleRequestService self,
                              Clock clock) {
        this.roleRequestRepository = roleRequestRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditTrailPort = auditTrailPort;
        this.workflowPort = workflowPort;
        this.assignRoleService = assignRoleService;
        this.eventPublisher = eventPublisher;
        this.self = self;
        this.clock = clock;
    }

    /**
     * Persists the request and its audit event only — starting the Camunda
     * process is <em>not</em> part of this transaction. {@code workflowPort.start}
     * is an external side effect; running it before the row committed meant a
     * later failure (e.g. the {@code attachProcessInstance} save, or the audit
     * write) rolled back the request while Camunda kept running a process that
     * pointed at a row that no longer existed. Publishing
     * {@link RoleRequestSubmittedEvent} instead defers the actual start to
     * {@link #onRoleRequestSubmitted}, which only runs once this transaction has
     * committed — see {@link TransactionalEventListener}.
     */
    @Transactional
    public RoleRequestResponse submit(UUID userId, UUID requestedRoleId, String ipAddress, String userAgent) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        if (roleRepository.findById(requestedRoleId).isEmpty()) {
            throw new RoleNotFoundException(requestedRoleId);
        }
        if (roleRequestRepository.existsActiveByUserIdAndRequestedRoleId(userId, requestedRoleId)) {
            throw new DuplicateActiveRoleRequestException(userId, requestedRoleId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        RoleRequest saved = roleRequestRepository.save(RoleRequest.create(userId, requestedRoleId, now));

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_REQUEST_SUBMITTED,
                userId,
                userId,
                now,
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                null,
                Map.of(
                        "roleRequestId", saved.getId().toString(),
                        "requestedRoleId", requestedRoleId.toString())
        ));

        eventPublisher.publishEvent(new RoleRequestSubmittedEvent(saved.getId(), userId, requestedRoleId));

        return mapToResponse(saved);
    }

    /**
     * Starts the Camunda process for a request that has just committed.
     * Idempotency key is the request id itself: if it already carries a
     * {@code processInstanceKey} (e.g. this listener somehow ran twice), starting
     * a second process is skipped. A failure here is caught and audited rather
     * than propagated — there is no HTTP request left to fail by this point —
     * leaving the request {@code PENDING} with no process instance, which is
     * exactly the state a manual or future automated retry needs to pick it back
     * up from; the API response the caller already received never claimed a
     * process had started.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoleRequestSubmitted(RoleRequestSubmittedEvent event) {
        boolean alreadyStarted = roleRequestRepository.findById(event.roleRequestId())
                .map(request -> request.getProcessInstanceKey() != null)
                .orElse(true);
        if (alreadyStarted) {
            return;
        }
        try {
            Long processInstanceKey = workflowPort.start(event.roleRequestId(), event.userId(), event.requestedRoleId());
            if (processInstanceKey != null) {
                self.attachProcessInstance(event.roleRequestId(), processInstanceKey);
            }
        } catch (RuntimeException workflowStartFailed) {
            logger.error("Failed to start Camunda process for role request {}: {}",
                    event.roleRequestId(), workflowStartFailed.getMessage(), workflowStartFailed);
            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.ROLE_REQUEST_WORKFLOW_START_FAILED,
                    event.userId(),
                    event.userId(),
                    LocalDateTime.now(clock),
                    null,
                    "role-request-submit-after-commit",
                    AuditResult.FAILED,
                    workflowStartFailed.getMessage(),
                    Map.of("roleRequestId", event.roleRequestId().toString())
            ));
        }
    }

    /**
     * Own transaction, separate from the {@code AFTER_COMMIT} listener's caller —
     * there is no longer an enclosing transaction to join at this point, so this
     * needs to open one itself.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachProcessInstance(UUID roleRequestId, long processInstanceKey) {
        roleRequestRepository.findById(roleRequestId).ifPresent(roleRequest -> {
            if (roleRequest.getProcessInstanceKey() == null) {
                roleRequest.attachProcessInstance(processInstanceKey);
                roleRequestRepository.save(roleRequest);
            }
        });
    }

    /**
     * Read-only status check used by {@code RejectRoleRequestWorker} to decide
     * whether to proceed (PENDING), skip as an idempotent redelivery (already
     * REJECTED), or refuse (any other terminal state) before calling
     * {@link #markRejected}. {@code ApproveRoleRequestWorker} does not need this:
     * {@link #approve} does the equivalent check itself, inside the same
     * transaction as the role grant it guards.
     */
    @Transactional(readOnly = true)
    public RoleRequestStatus currentStatus(UUID roleRequestId) {
        return roleRequestRepository.findById(roleRequestId)
                .orElseThrow(() -> new RoleRequestNotFoundException(roleRequestId))
                .getStatus();
    }

    /**
     * Admin entry point: signals the decision to Camunda by completing the pending
     * review user task. The actual {@code APPROVED}/{@code REJECTED} transition is
     * applied asynchronously by the corresponding job worker once the process
     * engine routes the gateway, not by this method.
     */
    @Transactional
    public RoleRequestResponse submitDecision(UUID roleRequestId, UUID reviewerId,
                                              RoleRequestDecision decision, String reason) {
        RoleRequest roleRequest = roleRequestRepository.findById(roleRequestId)
                .orElseThrow(() -> new RoleRequestNotFoundException(roleRequestId));
        roleRequest.requireDecidable();
        if (reviewerId.equals(roleRequest.getUserId())) {
            throw new SelfApprovalNotAllowedException(
                    String.format("Role request %s cannot be decided by the requesting user", roleRequestId));
        }

        workflowPort.submitDecision(roleRequest.getProcessInstanceKey(), decision.name(), reviewerId, reason);

        return mapToResponse(roleRequest);
    }

    /**
     * Called by {@code ApproveRoleRequestWorker} — the single entry point for
     * granting a role and settling the request, in one transaction. Assigning
     * the role and flipping the request to {@code APPROVED} used to be two
     * independently-committed calls; a failure in the second one (audit write,
     * bad reviewer FK) could leave the role granted while the request ended up
     * {@code FAILED} forever. Combining them here means either both happen or
     * neither does. {@link AssignRoleService#assignRoleToUser} is called with
     * {@code REQUIRED} propagation (the default), so it joins this method's
     * transaction instead of committing on its own.
     * <p>
     * The domain's own {@link RoleRequest#approve} guard runs <em>before</em>
     * the role is granted — an already-APPROVED request is a silent idempotent
     * no-op, and any other non-PENDING state throws immediately, so a
     * wrongly-routed job still can never grant a role for a request it isn't
     * allowed to approve.
     */
    @Transactional
    public void approve(UUID roleRequestId, UUID userId, UUID requestedRoleId, UUID reviewerId) {
        RoleRequest roleRequest = roleRequestRepository.findById(roleRequestId)
                .orElseThrow(() -> new RoleRequestNotFoundException(roleRequestId));
        if (roleRequest.getStatus() == RoleRequestStatus.APPROVED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        roleRequest.approve(reviewerId, now);

        assignRoleService.assignRoleToUser(userId, requestedRoleId, null, "camunda-worker");
        roleRequestRepository.save(roleRequest);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_REQUEST_APPROVED,
                reviewerId,
                roleRequest.getUserId(),
                now,
                null,
                "camunda-worker",
                AuditResult.SUCCESS,
                null,
                Map.of(
                        "roleRequestId", roleRequestId.toString(),
                        "requestedRoleId", roleRequest.getRequestedRoleId().toString())
        ));
    }

    /**
     * Called by {@code RejectRoleRequestWorker}. Never changes the user's roles.
     * Idempotent the same way {@link #approve} is.
     */
    @Transactional
    public void markRejected(UUID roleRequestId, UUID reviewerId, String reason) {
        RoleRequest roleRequest = roleRequestRepository.findById(roleRequestId)
                .orElseThrow(() -> new RoleRequestNotFoundException(roleRequestId));
        if (roleRequest.getStatus() == RoleRequestStatus.REJECTED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        roleRequest.reject(reviewerId, reason, now);
        roleRequestRepository.save(roleRequest);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("roleRequestId", roleRequestId.toString());
        metadata.put("requestedRoleId", roleRequest.getRequestedRoleId().toString());
        if (reason != null) {
            metadata.put("reason", reason);
        }

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_REQUEST_REJECTED,
                reviewerId,
                roleRequest.getUserId(),
                now,
                null,
                "camunda-worker",
                AuditResult.SUCCESS,
                null,
                metadata
        ));
    }

    /**
     * Called by either worker for a permanent failure (missing user/role, wrong
     * request state) or once retries are exhausted for a transient one. Idempotent
     * the same way {@link #approve} is.
     */
    @Transactional
    public void markFailed(UUID roleRequestId, String reason) {
        RoleRequest roleRequest = roleRequestRepository.findById(roleRequestId)
                .orElseThrow(() -> new RoleRequestNotFoundException(roleRequestId));
        if (roleRequest.getStatus() == RoleRequestStatus.FAILED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        roleRequest.markFailed(reason, now);
        roleRequestRepository.save(roleRequest);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_REQUEST_FAILED,
                roleRequest.getUserId(),
                roleRequest.getUserId(),
                now,
                null,
                "camunda-worker",
                AuditResult.FAILED,
                reason,
                Map.of("roleRequestId", roleRequestId.toString())
        ));
    }

    @Transactional(readOnly = true)
    public PageResult<RoleRequestResponse> getForUser(UUID userId, int page, int size, String sort) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        return roleRequestRepository.findByUserId(userId, PageRequest.of(page, size, parseSort(sort)))
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PageResult<RoleRequestResponse> getAll(int page, int size, String sort) {
        return roleRequestRepository.findAll(PageRequest.of(page, size, parseSort(sort)))
                .map(this::mapToResponse);
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Order.desc("createdAt"));
        }

        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";

        Sort.Order order = switch (direction) {
            case "desc" -> Sort.Order.desc(field);
            default -> Sort.Order.asc(field);
        };

        return Sort.by(order);
    }

    private RoleRequestResponse mapToResponse(RoleRequest roleRequest) {
        return new RoleRequestResponse(
                roleRequest.getId(),
                roleRequest.getUserId(),
                roleRequest.getRequestedRoleId(),
                roleRequest.getStatus(),
                roleRequest.getReviewerId(),
                roleRequest.getDecisionReason(),
                roleRequest.getCreatedAt(),
                roleRequest.getReviewedAt(),
                roleRequest.getUpdatedAt()
        );
    }
}
