package com.adventacademy.userservice.application.service.security;

import com.adventacademy.userservice.application.port.RsaKeyPairGeneratorPort;
import com.adventacademy.userservice.domain.model.RsaKeyPair;
import com.adventacademy.userservice.domain.repository.RsaKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class KeyRotationService {

    private final RsaKeyRepository rsaKeyRepository;
    private final RsaKeyPairGeneratorPort keyPairGenerator;
    private final long rotationWindowSeconds;
    private final long keyRetentionDays;
    private final Clock clock;

    public KeyRotationService(
            RsaKeyRepository rsaKeyRepository,
            RsaKeyPairGeneratorPort keyPairGenerator,
            @Value("${security.key-management.rotation-window-seconds:3600}") long rotationWindowSeconds,
            @Value("${security.key-management.key-retention-days:7}") long keyRetentionDays,
            Clock clock) {
        this.rsaKeyRepository = rsaKeyRepository;
        this.keyPairGenerator = keyPairGenerator;
        this.rotationWindowSeconds = rotationWindowSeconds;
        this.keyRetentionDays = keyRetentionDays;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${security.key-management.rotation-interval-days:90}d")
    @Transactional
    public void rotateKeysIfNeeded() {
        List<RsaKeyPair> activeKeys = rsaKeyRepository.findActive();

        if (activeKeys.isEmpty()) {
            log.warn("No active keys found. Generating new key pair.");
            generateNewKey();
            return;
        }

        // Check if any key needs rotation based on age
        RsaKeyPair oldestKey = activeKeys.stream()
                .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .get();

        Instant rotationThreshold = oldestKey.getCreatedAt().plusSeconds(
                90 * 86400 // 90 days in seconds
        );

        if (clock.instant().isAfter(rotationThreshold)) {
            log.info("Key rotation threshold reached. Rotating keys...");
            rotateKeys();
        }
    }

    @Transactional
    public void rotateKeys() {
        // Mark old keys as ROTATING
        List<RsaKeyPair> activeKeys = rsaKeyRepository.findActive();
        for (RsaKeyPair key : activeKeys) {
            key.markAsRotating();
            rsaKeyRepository.save(key);
            log.info("Marked key {} as ROTATING", key.getKid());
        }

        // Generate and save new key
        generateNewKey();

        // Schedule revocation of rotating keys after window
        log.info("Key rotation started. Old keys will be revoked after {} seconds", rotationWindowSeconds);
    }

    @Scheduled(fixedRateString = "${security.key-management.revocation-check-interval-minutes:15}m")
    @Transactional
    public void revokeExpiredRotatingKeys() {
        List<RsaKeyPair> rotatingKeys = rsaKeyRepository.findByStatus(RsaKeyPair.Status.ROTATING);

        Instant now = clock.instant();
        for (RsaKeyPair key : rotatingKeys) {
            if (key.getRotatedAt() != null && now.isAfter(key.getRotatedAt().plusSeconds(rotationWindowSeconds))) {
                key.markAsRevoked();
                rsaKeyRepository.save(key);
                log.info("Revoked rotating key {} after rotation window expired", key.getKid());
            }
        }
    }

    @Scheduled(fixedRateString = "${security.key-management.cleanup-interval-days:1}d")
    @Transactional
    public void cleanupOldRevokedKeys() {
        List<RsaKeyPair> revokedKeys = rsaKeyRepository.findByStatus(RsaKeyPair.Status.REVOKED);

        Instant cutoffTime = clock.instant().minusSeconds(keyRetentionDays * 86400);
        for (RsaKeyPair key : revokedKeys) {
            if (key.getRevokedAt() != null && key.getRevokedAt().isBefore(cutoffTime)) {
                // In production, implement proper deletion logic
                // For now just log
                log.info("Would delete old revoked key {} (revoked at {})", key.getKid(), key.getRevokedAt());
            }
        }
    }

    @Transactional
    public void emergencyRevokeKey(String kid) {
        RsaKeyPair key = rsaKeyRepository.findByKid(kid)
                .orElseThrow(() -> new IllegalArgumentException("Key not found: " + kid));

        key.markAsRevoked();
        rsaKeyRepository.save(key);

        log.warn("EMERGENCY: Key {} has been revoked", kid);
    }

    private void generateNewKey() {
        RsaKeyPair newKey = keyPairGenerator.generateNewKeyPair();
        rsaKeyRepository.save(newKey);
        log.info("Generated new RSA key pair with kid: {}", newKey.getKid());
    }
}
