package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.request.CreatePermissionRequest;
import com.adventacademy.userservice.application.dto.response.PermissionResponse;
import com.adventacademy.userservice.application.service.permission.CreatePermissionService;
import com.adventacademy.userservice.application.service.permission.GetPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for RBAC permission management.
 * <p>
 * Provides endpoints for creating atomic permissions and retrieving them
 * by ID or as a full list. Permissions are assigned to roles, not directly to users.
 * </p>
 */
@RestController
@RequestMapping("/api/permissions")
@Tag(name = "Permissions", description = "Permission management endpoints")
public class PermissionController {

    private final CreatePermissionService createPermissionService;
    private final GetPermissionService getPermissionService;

    public PermissionController(
            CreatePermissionService createPermissionService,
            GetPermissionService getPermissionService) {
        this.createPermissionService = createPermissionService;
        this.getPermissionService = getPermissionService;
    }

    @PostMapping
    @Operation(summary = "Create permission", description = "Create a new permission")
    @ApiResponse(responseCode = "201", description = "Permission created successfully")
    @ApiResponse(responseCode = "409", description = "Permission already exists")
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse response = createPermissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID", description = "Retrieve permission details by permission ID")
    @ApiResponse(responseCode = "200", description = "Permission found")
    @ApiResponse(responseCode = "404", description = "Permission not found")
    public ResponseEntity<PermissionResponse> getPermissionById(@PathVariable UUID id) {
        PermissionResponse response = getPermissionService.getPermissionById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all permissions", description = "Retrieve list of all permissions")
    @ApiResponse(responseCode = "200", description = "Permissions retrieved successfully")
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        List<PermissionResponse> response = getPermissionService.getAllPermissions();
        return ResponseEntity.ok(response);
    }
}
