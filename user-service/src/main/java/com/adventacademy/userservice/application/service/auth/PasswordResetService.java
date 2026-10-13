package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.CompletePasswordResetRequest;
import com.adventacademy.userservice.application.dto.request.PasswordResetRequest;
import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.InvalidTokenException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Use case service for the password-reset flow.
 * <p>
 * {@link #initiate} issues a one-time reset token and e-mails it; the response is identical whether
 * or not the address is known. {@link #complete} redeems the token, replaces the password, revokes
 * every refresh token of the account and records the outcome. Both steps are audited, including
 * refused tokens with their reason.
 */
@Service
public class PasswordResetService {

    static final String INITIATED_MESSAGE = "If the email exists, a reset link has been sent.";

    private final UserRepository userRepository;
    private final VerificationTokenService tokens;
    private final EmailDispatcher emails;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            VerificationTokenService tokens,
            EmailDispatcher emails,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            AuditTrailPort auditTrailPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokens = tokens;
        this.emails = emails;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse initiate(PasswordResetRequest request) {
        userRepository.findByEmail(request.email()).ifPresentOrElse(user -> {
            String token = tokens.issue(user.getId(), VerificationToken.PURPOSE_PASSWORD_RESET);
            emails.sendPasswordReset(user.getId(), user.getEmail(), token);
            auditTrailPort.record(event(AuditEventType.PASSWORD_RESET_INITIATED, user.getId(),
                    request.ipAddress(), request.userAgent(), AuditResult.SUCCESS, null));
        }, () -> auditTrailPort.record(event(AuditEventType.PASSWORD_RESET_INITIATED, null,
                request.ipAddress(), request.userAgent(), AuditResult.FAILED, "Unknown email")));

        return new MessageResponse(INITIATED_MESSAGE);
    }

    @Transactional
    public MessageResponse complete(CompletePasswordResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }

        VerificationToken redeemed;
        try {
            redeemed = tokens.redeem(request.token(), VerificationToken.PURPOSE_PASSWORD_RESET);
        } catch (InvalidTokenException e) {
            auditTrailPort.record(event(AuditEventType.PASSWORD_RESET_COMPLETED, null,
                    request.ipAddress(), request.userAgent(), AuditResult.FAILED, "token " + e.getReason()));
            throw e;
        }

        User user = userRepository.findByIdForUpdate(redeemed.getUserId())
                .orElseThrow(() -> new UserNotFoundException(redeemed.getUserId()));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.resetFailedAttempts();
        user.unlock();
        userRepository.save(user);

        jwtProvider.revokeRefreshTokens(user.getId());

        auditTrailPort.record(event(AuditEventType.PASSWORD_RESET_COMPLETED, user.getId(),
                request.ipAddress(), request.userAgent(), AuditResult.SUCCESS, null));
        auditTrailPort.record(event(AuditEventType.SESSIONS_REVOKED, user.getId(),
                request.ipAddress(), request.userAgent(), AuditResult.SUCCESS, "password reset"));

        return new MessageResponse("Password has been reset. Please log in with your new password.");
    }

    private AuditEvent event(AuditEventType type, UUID userId, String ip, String userAgent, AuditResult result, String reason) {
        return new AuditEvent(UUID.randomUUID(), type, userId, userId, LocalDateTime.now(clock), ip, userAgent, result, reason, null);
    }
}
