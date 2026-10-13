package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.ConfirmMfaResponse;
import com.adventacademy.userservice.application.dto.response.EnableMfaResponse;
import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.MfaProvider;
import com.adventacademy.userservice.application.port.MfaSecretCipher;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.RecoveryCode;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RecoveryCodeRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Use case service for the TOTP multi-factor lifecycle.
 * <p>
 * Enabling and disabling are step-up operations: the caller proves the current password on top of
 * the access token. The secret is stored encrypted ({@link MfaSecretCipher}) and {@code mfaEnabled}
 * only flips once the first code from the authenticator app verified, at which point the user gets
 * their recovery codes - once; only the digests are kept.
 */
@Service
public class MfaService {

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final MfaProvider mfaProvider;
    private final MfaSecretCipher secretCipher;
    private final MfaCodeVerifier codeVerifier;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;
    private final int recoveryCodeCount;

    public MfaService(UserRepository userRepository,
                      RecoveryCodeRepository recoveryCodeRepository,
                      MfaProvider mfaProvider,
                      MfaSecretCipher secretCipher,
                      MfaCodeVerifier codeVerifier,
                      PasswordEncoder passwordEncoder,
                      AuditTrailPort auditTrailPort,
                      Clock clock,
                      @Value("${security.mfa.recovery-code-count:8}") int recoveryCodeCount) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.mfaProvider = mfaProvider;
        this.secretCipher = secretCipher;
        this.codeVerifier = codeVerifier;
        this.passwordEncoder = passwordEncoder;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
        this.recoveryCodeCount = recoveryCodeCount;
    }

    @Transactional
    public EnableMfaResponse enableMfa(UUID userId, String password) {
        User user = load(userId);
        requirePassword(user, password, AuditEventType.MFA_ENROLLMENT_STARTED);

        if (user.isMfaEnabled()) {
            throw new InvalidCredentialsException("MFA is already enabled");
        }

        String secret = mfaProvider.generateSecret();
        user.setMfaSecret(secretCipher.encrypt(secret));
        user.setMfaLastUsedStep(null);
        userRepository.save(user);

        auditTrailPort.record(event(AuditEventType.MFA_ENROLLMENT_STARTED, user.getId(), AuditResult.SUCCESS, null));

        return new EnableMfaResponse(
                secret,
                mfaProvider.generateQrCodeUrl(user.getEmail(), secret),
                mfaProvider.generateQrCodeImage(user.getEmail(), secret));
    }

    @Transactional
    public ConfirmMfaResponse confirmMfa(UUID userId, String code) {
        User user = load(userId);

        if (user.getMfaSecret() == null) {
            throw new InvalidCredentialsException("MFA secret not found. Please enable MFA first.");
        }
        if (user.isMfaEnabled()) {
            throw new InvalidCredentialsException("MFA is already enabled");
        }
        if (!codeVerifier.verifyTotp(user, code)) {
            auditTrailPort.record(event(AuditEventType.MFA_ENABLED, user.getId(), AuditResult.FAILED, "Invalid MFA code"));
            throw new InvalidCredentialsException("Invalid MFA code");
        }

        user.setMfaEnabled(true);
        userRepository.save(user);

        List<String> recoveryCodes = issueRecoveryCodes(user.getId());

        auditTrailPort.record(event(AuditEventType.MFA_ENABLED, user.getId(), AuditResult.SUCCESS, null));

        return new ConfirmMfaResponse("MFA enabled successfully. Store the recovery codes now - they are not shown again.",
                recoveryCodes);
    }

    @Transactional
    public MessageResponse disableMfa(UUID userId, String password, String code) {
        User user = load(userId);
        requirePassword(user, password, AuditEventType.MFA_DISABLED);

        if (!user.isMfaEnabled()) {
            throw new InvalidCredentialsException("MFA is not enabled");
        }

        MfaCodeVerifier.Outcome outcome = codeVerifier.verify(user, code);
        if (outcome == MfaCodeVerifier.Outcome.REJECTED) {
            auditTrailPort.record(event(AuditEventType.MFA_DISABLED, user.getId(), AuditResult.FAILED, "Invalid MFA code"));
            throw new InvalidCredentialsException("Invalid MFA code");
        }

        user.clearMfa();
        userRepository.save(user);
        recoveryCodeRepository.deleteAllByUserId(user.getId());

        auditTrailPort.record(event(AuditEventType.MFA_DISABLED, user.getId(), AuditResult.SUCCESS,
                outcome == MfaCodeVerifier.Outcome.RECOVERY_CODE ? "recovery code" : null));

        return new MessageResponse("MFA disabled successfully");
    }

    private List<String> issueRecoveryCodes(UUID userId) {
        recoveryCodeRepository.deleteAllByUserId(userId);
        Instant now = clock.instant();
        List<String> plaintext = new ArrayList<>(recoveryCodeCount);
        List<RecoveryCode> hashed = new ArrayList<>(recoveryCodeCount);
        for (int i = 0; i < recoveryCodeCount; i++) {
            String code = OpaqueTokens.newRecoveryCode();
            plaintext.add(code);
            hashed.add(RecoveryCode.create(userId, OpaqueTokens.digest(OpaqueTokens.normalizeRecoveryCode(code)), now));
        }
        recoveryCodeRepository.saveAll(hashed);
        return List.copyOf(plaintext);
    }

    private void requirePassword(User user, String password, AuditEventType operation) {
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            auditTrailPort.record(event(operation, user.getId(), AuditResult.FAILED, "Step-up password check failed"));
            throw new InvalidCredentialsException("Invalid password");
        }
    }

    private User load(UUID userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private AuditEvent event(AuditEventType type, UUID userId, AuditResult result, String reason) {
        return new AuditEvent(UUID.randomUUID(), type, userId, userId, LocalDateTime.now(clock), null, null, result, reason, null);
    }
}
