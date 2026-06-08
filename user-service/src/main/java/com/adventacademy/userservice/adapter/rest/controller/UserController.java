package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.service.security.AccountLockService;
import com.adventacademy.userservice.application.service.user.DeleteUserService;
import com.adventacademy.userservice.application.service.user.GetUserService;
import com.adventacademy.userservice.domain.pagination.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * REST controller for user management operations.
 * <p>
 * Provides endpoints for retrieving users by ID or listing all users,
 * soft-deleting users, and unlocking locked accounts.
 * Intended for use by administrators with appropriate authorization.
 * </p>
 */

@Validated
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    private final GetUserService getUserService;
    private final DeleteUserService deleteUserService;
    private final AccountLockService accountLockService;

    public UserController(
            GetUserService getUserService,
            DeleteUserService deleteUserService,
            AccountLockService accountLockService) {
        this.getUserService = getUserService;
        this.deleteUserService = deleteUserService;
        this.accountLockService = accountLockService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve user details by user ID")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse response = getUserService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve a paginated list of users")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    public ResponseEntity<PageResult<UserResponse>> getAllUsers(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Number of users per page, maximum 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Sort format: field,direction. Example: id,asc or createdAt,desc", example = "id,asc")
            @RequestParam(defaultValue = "id,asc") String sort
    ) {
        PageResult<UserResponse> response = getUserService.getAllUsers(page, size, sort);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Soft delete user by ID")
    @ApiResponse(responseCode = "204", description = "User deleted successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        deleteUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Unlock user account", description = "Unlock a locked user account")
    @ApiResponse(responseCode = "200", description = "Account unlocked successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<MessageResponse> unlockAccount(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        accountLockService.unlockAccount(id, ipAddress, userAgent);
        return ResponseEntity.ok(new MessageResponse("Account unlocked successfully"));
    }

}
