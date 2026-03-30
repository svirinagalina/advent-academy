package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.dto.request.CreateRoleRequest;
import com.adventacademy.userservice.application.dto.response.RoleResponse;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Use case service for creating new RBAC roles.
 * <p>
 * Validates that the role name is unique, then persists a new non-system role.
 * System roles (e.g. ADMIN, USER) are seeded separately and cannot be created
 * through this service.
 * </p>
 */
@Service
public class CreateRoleService {

    private final RoleRepository roleRepository;

    public CreateRoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new InvalidUserStateException("Role already exists: " + request.name());
        }

        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        role.setSystemRole(false);

        Role savedRole = roleRepository.save(role);

        return mapToRoleResponse(savedRole);
    }

    private RoleResponse mapToRoleResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions().stream()
                        .map(p -> p.getName())
                        .collect(Collectors.toSet()),
                role.isSystemRole(),
                role.getCreatedAt()
        );
    }
}
