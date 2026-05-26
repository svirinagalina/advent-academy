package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.service.security.AccountLockService;
import com.adventacademy.userservice.application.service.user.DeleteUserService;
import com.adventacademy.userservice.application.service.user.GetUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user management operations.
 * <p>
 * Provides endpoints for retrieving users by ID or listing all users,
 * soft-deleting users, and unlocking locked accounts.
 * Intended for use by administrators with appropriate authorization.
 * </p>
 */
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
    @Operation(summary = "Get all users", description = "Retrieve list of all users")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> response = getUserService.getAllUsers();
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
