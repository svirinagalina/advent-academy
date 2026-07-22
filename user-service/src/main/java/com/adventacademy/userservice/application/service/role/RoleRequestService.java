package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.dto.response.RoleRequestResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.exception.DuplicateActiveRoleRequestException;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;
import com.adventacademy.userservice.domain.pagination.Sort;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.domain.repository.RoleRequestRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Application use case for role approval requests.
 *
 * <p>Creates durable `RoleRequest` records, enforces the active duplicate rule,
 * records submit audit events, and exposes read models for user/admin views.
 * Camunda workers should call this layer later instead of touching persistence
 * directly.</p>
 */
@Service
public class RoleRequestService {

    private final RoleRequestRepository roleRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public RoleRequestService(RoleRequestRepository roleRequestRepository,
                              UserRepository userRepository,
                              RoleRepository roleRepository,
                              AuditTrailPort auditTrailPort,
                              Clock clock) {
        this.roleRequestRepository = roleRequestRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

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

        return mapToResponse(saved);
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
