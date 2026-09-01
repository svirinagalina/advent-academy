package com.adventacademy.userservice.infrastructure.config;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import com.adventacademy.userservice.domain.repository.RsaKeyRepository;
import com.adventacademy.userservice.infrastructure.security.RsaKeyPairGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityKeyConfiguration {

    private final RsaKeyRepository rsaKeyRepository;
    private final RsaKeyPairGenerator keyGenerator;

    public SecurityKeyConfiguration(RsaKeyRepository rsaKeyRepository, RsaKeyPairGenerator keyGenerator) {
        this.rsaKeyRepository = rsaKeyRepository;
        this.keyGenerator = keyGenerator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeKeysOnStartup() {
        var activeKeys = rsaKeyRepository.findActive();

        if (activeKeys.isEmpty()) {
            log.info("No active RSA keys found. Generating initial key pair...");
            RsaKeyPair newKeyPair = keyGenerator.generateNewKeyPair();
            rsaKeyRepository.save(newKeyPair);
            log.info("Initial RSA key pair generated and saved with kid: {}", newKeyPair.getKid());
        } else {
            log.info("Found {} active RSA key(s)", activeKeys.size());
        }
    }
}
