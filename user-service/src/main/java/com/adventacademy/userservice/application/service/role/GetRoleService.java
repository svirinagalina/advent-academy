package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.dto.response.RoleResponse;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Use case service for retrieving RBAC roles.
 * <p>
 * Provides read-only access to a role by its ID or to the full list of roles,
 * including the set of permissions attached to each role.
 * </p>
 */
@Service
public class GetRoleService {

    private final RoleRepository roleRepository;

    public GetRoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        return mapToRoleResponse(role);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
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
