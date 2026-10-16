package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.request.ChangePasswordRequest;
import com.adventacademy.userservice.application.dto.request.CompletePasswordResetRequest;
import com.adventacademy.userservice.application.dto.request.ConfirmMfaRequest;
import com.adventacademy.userservice.application.dto.request.DisableMfaRequest;
import com.adventacademy.userservice.application.dto.request.EnableMfaRequest;
import com.adventacademy.userservice.application.dto.request.LoginRequest;
import com.adventacademy.userservice.application.dto.request.PasswordResetRequest;
import com.adventacademy.userservice.application.dto.request.RefreshTokenRequest;
import com.adventacademy.userservice.application.dto.request.RegisterUserRequest;
import com.adventacademy.userservice.application.dto.request.ResendVerificationRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.dto.response.ConfirmMfaResponse;
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
 * email confirmation and re-sending, password change and reset, and MFA setup/teardown.
 * The registration, login, refresh, confirm/resend and password-reset endpoints are public; the
 * password-change and MFA endpoints need an access token, and enabling/disabling MFA additionally
 * requires the current password (step-up). The public endpoints are rate limited per address and
 * per account, and answer identically whether or not an account exists.
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication and registration endpoints")
public class AuthController {

    private final com.adventacademy.userservice.application.port.JwtProvider jwtProvider;
    private final RegisterUserService registerUserService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final ConfirmEmailService confirmEmailService;
    private final ChangePasswordService changePasswordService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;

    public AuthController(
            com.adventacademy.userservice.application.port.JwtProvider jwtProvider,
            RegisterUserService registerUserService,
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            ConfirmEmailService confirmEmailService,
            ChangePasswordService changePasswordService,
            PasswordResetService passwordResetService,
            MfaService mfaService) {
        this.jwtProvider = jwtProvider;
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

    @org.springframework.beans.factory.annotation.Autowired
    private RefreshRotationCoordinator rotations;

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token")
    @ApiResponse(responseCode = "200", description = "Token refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = "X-Refresh-Request", defaultValue = "") String requestKey) {
        AuthResponse response = rotations.rotate(request, requestKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = "X-Refresh-Request", defaultValue = "") String requestKey) {
        // consumeRefreshToken verifies signature/type/expiry and revokes only this session.
        // Already revoked/expired tokens still yield a successful, idempotent logout.
        rotations.logout(request, requestKey);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm-email")
    @Operation(summary = "Confirm email", description = "Confirm user email address with verification token")
    @ApiResponse(responseCode = "200", description = "Email confirmed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid, expired or already used verification token")
    public ResponseEntity<MessageResponse> confirmEmail(
            @RequestParam String token,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        MessageResponse response = confirmEmailService.confirmEmail(token, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Re-send verification email",
            description = "Issues a new verification link for a pending account. The response is identical "
                    + "whether the email is known, unknown or already verified.")
    @ApiResponse(responseCode = "200", description = "Request accepted")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest httpRequest) {
        MessageResponse response = confirmEmailService.resendVerification(
                request.email(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
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
            description = "Sends a one-time reset link to the address. The response is identical "
                    + "regardless of whether the email is known, to avoid leaking account existence.")
    @ApiResponse(responseCode = "200", description = "Password-reset initiation accepted")
    @ApiResponse(responseCode = "429", description = "Too many requests")
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

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Complete password reset",
            description = "Sets a new password using the token from the reset email. The token is single-use; "
                    + "all refresh sessions of the account are revoked.")
    @ApiResponse(responseCode = "200", description = "Password reset")
    @ApiResponse(responseCode = "400", description = "Invalid, expired or already used token, or passwords do not match")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<MessageResponse> completePasswordReset(
            @Valid @RequestBody CompletePasswordResetRequest request,
            HttpServletRequest httpRequest) {
        CompletePasswordResetRequest enriched = new CompletePasswordResetRequest(
                request.token(),
                request.newPassword(),
                request.confirmPassword(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        MessageResponse response = passwordResetService.complete(enriched);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Start MFA enrolment",
            description = "Step-up: requires the current password. Returns the secret, the otpauth URI and a QR "
                    + "code; MFA is only active after /mfa/confirm.")
    @ApiResponse(responseCode = "200", description = "MFA secret generated")
    @ApiResponse(responseCode = "401", description = "Invalid password, or MFA already enabled")
    public ResponseEntity<EnableMfaResponse> enableMfa(
            @Valid @RequestBody EnableMfaRequest request,
            Authentication authentication) {
        EnableMfaResponse response = mfaService.enableMfa(userId(authentication), request.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/confirm")
    @Operation(summary = "Confirm MFA enrolment",
            description = "Verifies the first code from the authenticator app, enables MFA and returns the "
                    + "recovery codes. They are shown exactly once.")
    @ApiResponse(responseCode = "200", description = "MFA enabled; recovery codes in the response")
    @ApiResponse(responseCode = "401", description = "Invalid MFA code")
    public ResponseEntity<ConfirmMfaResponse> confirmMfa(
            @Valid @RequestBody ConfirmMfaRequest request,
            Authentication authentication) {
        ConfirmMfaResponse response = mfaService.confirmMfa(userId(authentication), request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Disable MFA",
            description = "Step-up: requires the current password and a current TOTP or an unused recovery code.")
    @ApiResponse(responseCode = "200", description = "MFA disabled successfully")
    @ApiResponse(responseCode = "401", description = "Invalid password or MFA code")
    public ResponseEntity<MessageResponse> disableMfa(
            @Valid @RequestBody DisableMfaRequest request,
            Authentication authentication) {
        MessageResponse response = mfaService.disableMfa(userId(authentication), request.password(), request.code());
        return ResponseEntity.ok(response);
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
