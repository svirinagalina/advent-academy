package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Component
public class RsaKeyPairGenerator {

    private static final int KEY_SIZE = 2048;
    private static final String ALGORITHM = "RS256";

    public RsaKeyPair generateNewKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(KEY_SIZE);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();

            String privateKeyPem = convertPrivateKeyToPem(privateKey);
            String publicKeyPem = convertPublicKeyToPem(publicKey);

            String kid = UUID.randomUUID().toString();

            log.info("Generated new RSA key pair with kid: {}", kid);

            return RsaKeyPair.create(kid, ALGORITHM, privateKeyPem, publicKeyPem);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate RSA key pair", e);
            throw new RuntimeException("RSA algorithm not available", e);
        }
    }

    private String convertPrivateKeyToPem(PrivateKey privateKey) {
        byte[] encoded = privateKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN PRIVATE KEY-----\n" +
                base64Encoded.replaceAll("(.{64})", "$1\n") +
                "\n-----END PRIVATE KEY-----";
    }

    private String convertPublicKeyToPem(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64Encoded.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }
}
