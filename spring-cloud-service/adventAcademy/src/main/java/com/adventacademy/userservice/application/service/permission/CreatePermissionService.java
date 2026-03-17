package com.adventacademy.userservice.application.service.permission;

import com.adventacademy.userservice.application.dto.request.CreatePermissionRequest;
import com.adventacademy.userservice.application.dto.response.PermissionResponse;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
import com.adventacademy.userservice.domain.model.Permission;
import com.adventacademy.userservice.domain.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case service for creating atomic RBAC permissions.
 * <p>
 * Validates uniqueness of the permission name and persists the new permission.
 * Permissions are then assigned to roles, not directly to users.
 * </p>
 */
@Service
public class CreatePermissionService {

    private final PermissionRepository permissionRepository;

    public CreatePermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByName(request.name())) {
            throw new InvalidUserStateException("Permission already exists: " + request.name());
        }

        Permission permission = new Permission();
        permission.setName(request.name());
        permission.setDescription(request.description());

        Permission savedPermission = permissionRepository.save(permission);

        return new PermissionResponse(
                savedPermission.getId(),
                savedPermission.getName(),
                savedPermission.getDescription(),
                savedPermission.getCreatedAt()
        );
    }
}
