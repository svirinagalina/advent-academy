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
    private final com.adventacademy.userservice.application.service.admin.UserAdminPolicy adminPolicy;

    public AssignRoleService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditTrailPort auditTrailPort,
            Clock clock, com.adventacademy.userservice.application.service.admin.UserAdminPolicy adminPolicy) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
        this.adminPolicy = adminPolicy;
    }

    /**
     * Idempotent: safe to call more than once for the same user/role pair (e.g. a
     * redelivered Camunda job). {@code Role} has no {@code equals}/{@code hashCode}
     * override and {@code user_roles} has a composite primary key, so a second
     * {@code user.addRole(role)} with a freshly-loaded entity is a genuine duplicate
     * insert, not a harmless Set no-op — checking {@code hasRole} first avoids it.
     */
    @Transactional
    public UserResponse assignRoleToUser(UUID userId, UUID roleId, String ipAddress, String userAgent) {
        adminPolicy.lock();
        adminPolicy.authorizeRequestIfPresent();
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (user.hasRole(role.getName())) {
            return mapToUserResponse(user);
        }

        var before=user.getRoles().stream().map(Role::getName).toList();
        user.addRole(role);
        User savedUser = userRepository.save(user);
        adminPolicy.recordRoleChange(userId,before,savedUser.getRoles().stream().map(Role::getName).toList());

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_ASSIGNED,
                adminPolicy.actorOr(userId),
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
        adminPolicy.lock();
        adminPolicy.authorizeRequestIfPresent();
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if ("ADMIN".equals(role.getName())) adminPolicy.protect(userId);
        var before=user.getRoles().stream().map(Role::getName).toList();
        user.removeRole(role);
        User savedUser = userRepository.save(user);
        adminPolicy.recordRoleChange(userId,before,savedUser.getRoles().stream().map(Role::getName).toList());

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_REVOKED,
                adminPolicy.actorOr(userId),
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