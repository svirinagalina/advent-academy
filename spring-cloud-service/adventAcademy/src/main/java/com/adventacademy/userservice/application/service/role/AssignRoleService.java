package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Use case service for managing role assignments on users.
 * <p>
 * Allows granting a role to a user and revoking it. Both operations
 * validate that the user and role exist before modifying the relationship.
 * </p>
 */
@Service
public class AssignRoleService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AssignRoleService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public UserResponse assignRoleToUser(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        user.addRole(role);
        User savedUser = userRepository.save(user);

        return mapToUserResponse(savedUser);
    }

    @Transactional
    public UserResponse removeRoleFromUser(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        user.removeRole(role);
        User savedUser = userRepository.save(user);

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
