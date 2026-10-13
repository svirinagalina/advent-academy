package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.MfaProvider;
import com.adventacademy.userservice.application.port.MfaSecretCipher;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RecoveryCodeRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.OptionalLong;

/**
 * Second-factor check shared by login and MFA teardown: a six-digit TOTP or a one-time recovery code.
 * <p>
 * The TOTP path decrypts the stored seed, asks the provider which time step the code belongs to
 * and refuses any step that is not newer than the last accepted one, so a captured code cannot be
 * replayed - on this replica or any other, because the step lives on the user row. The recovery
 * path burns the code atomically in the database.
 */
@Component
public class MfaCodeVerifier {

    public enum Outcome { TOTP, RECOVERY_CODE, REJECTED }

    private final MfaProvider mfaProvider;
    private final MfaSecretCipher secretCipher;
    private final RecoveryCodeRepository recoveryCodes;
    private final Clock clock;

    public MfaCodeVerifier(MfaProvider mfaProvider,
                           MfaSecretCipher secretCipher,
                           RecoveryCodeRepository recoveryCodes,
                           Clock clock) {
        this.mfaProvider = mfaProvider;
        this.secretCipher = secretCipher;
        this.recoveryCodes = recoveryCodes;
        this.clock = clock;
    }

    /**
     * Verifies the code and, on success, advances the user's replay guard (TOTP) or burns the
     * recovery code. The caller must hold the user row's write lock until it persists the user
     * and commits; otherwise two requests could both accept the same TOTP step.
     */
    public Outcome verify(User user, String code) {
        if (code == null || code.isBlank() || user.getMfaSecret() == null) {
            return Outcome.REJECTED;
        }
        if (OpaqueTokens.looksLikeRecoveryCode(code)) {
            String hash = OpaqueTokens.digest(OpaqueTokens.normalizeRecoveryCode(code));
            return recoveryCodes.consume(user.getId(), hash, clock.instant()) ? Outcome.RECOVERY_CODE : Outcome.REJECTED;
        }
        return verifyTotp(user, code) ? Outcome.TOTP : Outcome.REJECTED;
    }

    /** TOTP only - used while enrolment is confirmed, when no recovery codes exist yet. */
    public boolean verifyTotp(User user, String code) {
        String secret;
        try {
            secret = secretCipher.decrypt(user.getMfaSecret());
        } catch (IllegalArgumentException undecryptable) {
            return false;
        }
        OptionalLong step = mfaProvider.verifyCode(secret, code);
        if (step.isEmpty()) {
            return false;
        }
        Long lastUsed = user.getMfaLastUsedStep();
        if (lastUsed != null && step.getAsLong() <= lastUsed) {
            return false;
        }
        user.setMfaLastUsedStep(step.getAsLong());
        return true;
    }
}
