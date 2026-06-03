package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssignRoleService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public AssignRoleService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditTrailPort auditTrailPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public UserResponse assignRoleToUser(UUID userId, UUID roleId, String ipAddress, String userAgent) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        user.addRole(role);
        User savedUser = userRepository.save(user);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_ASSIGNED,
                userId,
                userId,
                LocalDateTime.now(clock),
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                null,
                Map.of("roleId", roleId.toString(), "roleName", role.getName())
        ));

        return mapToUserResponse(savedUser);
    }

    @Transactional
    public UserResponse removeRoleFromUser(UUID userId, UUID roleId, String ipAddress, String userAgent) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        user.removeRole(role);
        User savedUser = userRepository.save(user);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_REVOKED,
                userId,
                userId,
                LocalDateTime.now(clock),
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                null,
                Map.of("roleId", roleId.toString(), "roleName", role.getName())
        ));

        return mapToUserResponse(savedUser);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                user.getRoles().stream()
                        .map(r -> r.getName())
                        .collect(Collectors.toSet()),
                user.isEmailVerified(),
                user.isMfaEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}