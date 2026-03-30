package com.adventacademy.userservice.application.service.permission;

import com.adventacademy.userservice.application.dto.response.PermissionResponse;
import com.adventacademy.userservice.domain.exception.PermissionNotFoundException;
import com.adventacademy.userservice.domain.model.Permission;
import com.adventacademy.userservice.domain.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Use case service for retrieving RBAC permissions.
 * <p>
 * Provides read-only access to a permission by its ID or to the complete
 * list of permissions defined in the system.
 * </p>
 */
@Service
public class GetPermissionService {

    private final PermissionRepository permissionRepository;

    public GetPermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermissionById(UUID permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new PermissionNotFoundException(permissionId));
        return mapToPermissionResponse(permission);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
    }

    private PermissionResponse mapToPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getDescription(),
                permission.getCreatedAt()
        );
    }
}
