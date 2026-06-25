package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.request.CreateRoleRequest;
import com.adventacademy.userservice.application.dto.response.RoleResponse;
import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.service.role.AssignRoleService;
import com.adventacademy.userservice.application.service.role.CreateRoleService;
import com.adventacademy.userservice.application.service.role.GetRoleService;
import com.adventacademy.userservice.domain.pagination.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for RBAC role management.
 * <p>
 * Provides endpoints for creating roles, retrieving roles by ID or listing all roles,
 * and assigning or revoking roles from users.
 * </p>
 */


@Validated
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Role management endpoints")
public class RoleController {

    private final CreateRoleService createRoleService;
    private final GetRoleService getRoleService;
    private final AssignRoleService assignRoleService;

    public RoleController(
            CreateRoleService createRoleService,
            GetRoleService getRoleService,
            AssignRoleService assignRoleService) {
        this.createRoleService = createRoleService;
        this.getRoleService = getRoleService;
        this.assignRoleService = assignRoleService;
    }

    @PostMapping
    @Operation(summary = "Create role", description = "Create a new role")
    @ApiResponse(responseCode = "201", description = "Role created successfully")
    @ApiResponse(responseCode = "409", description = "Role already exists")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = createRoleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID", description = "Retrieve role details by role ID")
    @ApiResponse(responseCode = "200", description = "Role found")
    @ApiResponse(responseCode = "404", description = "Role not found")
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable UUID id) {
        RoleResponse response = getRoleService.getRoleById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all roles", description = "Retrieve paginated list of roles")
    @ApiResponse(responseCode = "200", description = "Roles retrieved successfully")
    public ResponseEntity<PageResult<RoleResponse>> getAllRoles(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "id,asc") String sort
    ) {
        PageResult<RoleResponse> response = getRoleService.getAllRoles(page, size, sort);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/roles/{roleId}")
    @Operation(summary = "Assign role to user", description = "Assign a role to a user")
    @ApiResponse(responseCode = "200", description = "Role assigned successfully")
    @ApiResponse(responseCode = "404", description = "User or role not found")
    public ResponseEntity<UserResponse> assignRoleToUser(
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        UserResponse response = assignRoleService.assignRoleToUser(userId, roleId, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @Operation(summary = "Remove role from user", description = "Remove a role from a user")
    @ApiResponse(responseCode = "200", description = "Role removed successfully")
    @ApiResponse(responseCode = "404", description = "User or role not found")
    public ResponseEntity<UserResponse> removeRoleFromUser(
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        UserResponse response = assignRoleService.removeRoleFromUser(userId, roleId, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }
}
