package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.request.ChangePasswordRequest;
import com.adventacademy.userservice.application.dto.request.LoginRequest;
import com.adventacademy.userservice.application.dto.request.PasswordResetRequest;
import com.adventacademy.userservice.application.dto.request.RefreshTokenRequest;
import com.adventacademy.userservice.application.dto.request.RegisterUserRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.dto.response.EnableMfaResponse;
import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.service.auth.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller handling user authentication and registration.
 * <p>
 * Provides endpoints for user registration, login with optional MFA, JWT token refresh,
 * email confirmation, password change, and MFA setup/teardown.
 * All endpoints are public and do not require prior authentication.
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication and registration endpoints")
public class AuthController {

    private final RegisterUserService registerUserService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final ConfirmEmailService confirmEmailService;
    private final ChangePasswordService changePasswordService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;

    public AuthController(
            RegisterUserService registerUserService,
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            ConfirmEmailService confirmEmailService,
            ChangePasswordService changePasswordService,
            PasswordResetService passwordResetService,
            MfaService mfaService) {
        this.registerUserService = registerUserService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.confirmEmailService = confirmEmailService;
        this.changePasswordService = changePasswordService;
        this.passwordResetService = passwordResetService;
        this.mfaService = mfaService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Register a new user account. Email verification required before login.")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "409", description = "User with email already exists")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse response = registerUserService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and get JWT tokens")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "403", description = "Account locked or not verified")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        LoginRequest enrichedRequest = new LoginRequest(
                request.email(),
                request.password(),
                request.mfaCode(),
                ipAddress,
                userAgent
        );

        AuthResponse response = loginService.login(enrichedRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token")
    @ApiResponse(responseCode = "200", description = "Token refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = refreshTokenService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm-email")
    @Operation(summary = "Confirm email", description = "Confirm user email address with verification token")
    @ApiResponse(responseCode = "200", description = "Email confirmed successfully")
    @ApiResponse(responseCode = "404", description = "Invalid verification token")
    public ResponseEntity<MessageResponse> confirmEmail(
            @RequestParam String token,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        MessageResponse response = confirmEmailService.confirmEmail(token, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Change user password")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid old password")

    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        ChangePasswordRequest enrichedRequest = new ChangePasswordRequest(
                request.oldPassword(),
                request.newPassword(),
                ipAddress,
                userAgent
        );

        MessageResponse response = changePasswordService.changePassword(userId(authentication), enrichedRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset")
    @Operation(summary = "Initiate password reset",
            description = "Records a password-reset request for audit. The response is identical "
                    + "regardless of whether the email is known, to avoid leaking account existence.")
    @ApiResponse(responseCode = "200", description = "Password-reset initiation accepted")
    public ResponseEntity<MessageResponse> initiatePasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        PasswordResetRequest enriched = new PasswordResetRequest(
                request.email(),
                ipAddress,
                userAgent
        );

        MessageResponse response = passwordResetService.initiate(enriched);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Enable MFA", description = "Enable multi-factor authentication and get QR code")
    @ApiResponse(responseCode = "200", description = "MFA secret generated")
    public ResponseEntity<EnableMfaResponse> enableMfa(Authentication authentication) {
        EnableMfaResponse response = mfaService.enableMfa(userId(authentication));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/confirm")
    @Operation(summary = "Confirm MFA", description = "Confirm MFA setup with code from authenticator app")
    @ApiResponse(responseCode = "200", description = "MFA enabled successfully")
    @ApiResponse(responseCode = "401", description = "Invalid MFA code")
    public ResponseEntity<MessageResponse> confirmMfa(
            @RequestParam String code,
            Authentication authentication) {
        MessageResponse response = mfaService.confirmMfa(userId(authentication), code);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Disable MFA", description = "Disable multi-factor authentication")
    @ApiResponse(responseCode = "200", description = "MFA disabled successfully")
    @ApiResponse(responseCode = "401", description = "Invalid MFA code")
    public ResponseEntity<MessageResponse> disableMfa(
            @RequestParam String code,
            Authentication authentication) {
        MessageResponse response = mfaService.disableMfa(userId(authentication), code);
        return ResponseEntity.ok(response);
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
