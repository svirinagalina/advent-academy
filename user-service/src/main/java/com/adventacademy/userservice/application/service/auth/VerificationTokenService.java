package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.domain.exception.InvalidTokenException;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues and redeems the one-time tokens behind e-mail verification and password reset.
 * <p>
 * Only the SHA-256 digest of a token is stored; the plaintext exists in the e-mail and nowhere
 * else. Redeeming is an atomic "mark used if still unused" update, so of two concurrent requests
 * with the same token exactly one succeeds.
 */
@Service
public class VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;
    private final Clock clock;
    private final Duration emailVerificationTtl;
    private final Duration passwordResetTtl;

    public VerificationTokenService(
            VerificationTokenRepository tokenRepository,
            Clock clock,
            @Value("${security.tokens.email-verification-ttl:24h}") Duration emailVerificationTtl,
            @Value("${security.tokens.password-reset-ttl:1h}") Duration passwordResetTtl) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
        this.emailVerificationTtl = emailVerificationTtl;
        this.passwordResetTtl = passwordResetTtl;
    }

    /**
     * Creates a fresh token for the user, retiring any still-active one for the same purpose.
     *
     * @return the plaintext token to put in the e-mail; it is not kept anywhere
     */
    @Transactional
    public String issue(UUID userId, String purpose) {
        Instant now = clock.instant();
        tokenRepository.invalidateActive(userId, purpose, now);

        String plaintext = OpaqueTokens.newToken();
        tokenRepository.save(VerificationToken.create(userId, OpaqueTokens.digest(plaintext), purpose, now, now.plus(ttl(purpose))));
        return plaintext;
    }

    /**
     * Consumes the token.
     *
     * @return the token, whose {@code userId} identifies the account the action applies to
     * @throws InvalidTokenException when the token is unknown, expired, already used, issued for a
     *                               different purpose, or lost the race to a concurrent request
     */
    @Transactional
    public VerificationToken redeem(String plaintext, String expectedPurpose) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new InvalidTokenException("unknown");
        }
        Instant now = clock.instant();
        VerificationToken token = tokenRepository.findByTokenDigest(OpaqueTokens.digest(plaintext))
                .orElseThrow(() -> new InvalidTokenException("unknown"));

        if (!token.getPurpose().equals(expectedPurpose)) {
            throw new InvalidTokenException("wrong_purpose");
        }
        if (token.isUsed()) {
            throw new InvalidTokenException("used");
        }
        if (token.isExpired(now)) {
            throw new InvalidTokenException("expired");
        }
        if (!tokenRepository.consume(token.getId(), now)) {
            throw new InvalidTokenException("used");
        }
        token.markAsUsed(now);
        return token;
    }

    private Duration ttl(String purpose) {
        return VerificationToken.PURPOSE_PASSWORD_RESET.equals(purpose) ? passwordResetTtl : emailVerificationTtl;
    }
}
