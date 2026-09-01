package com.adventacademy.userservice.domain.model;

import java.time.Instant;
import java.util.UUID;

public class RsaKeyPair {

    public enum Status {
        ACTIVE,
        ROTATING,
        REVOKED,
        EXPIRED
    }

    private UUID id;
    private String kid;
    private String algorithm;
    private String privateKeyPem;
    private String publicKeyPem;
    private Status status;
    private Instant createdAt;
    private Instant rotatedAt;
    private Instant revokedAt;
    private String createdBy;

    public RsaKeyPair(UUID id, String kid, String algorithm, String privateKeyPem, String publicKeyPem,
                      Status status, Instant createdAt, Instant rotatedAt, Instant revokedAt, String createdBy) {
        this.id = id;
        this.kid = kid;
        this.algorithm = algorithm;
        this.privateKeyPem = privateKeyPem;
        this.publicKeyPem = publicKeyPem;
        this.status = status;
        this.createdAt = createdAt;
        this.rotatedAt = rotatedAt;
        this.revokedAt = revokedAt;
        this.createdBy = createdBy;
    }

    public static RsaKeyPair create(String kid, String algorithm, String privateKeyPem, String publicKeyPem) {
        return new RsaKeyPair(
                UUID.randomUUID(),
                kid,
                algorithm,
                privateKeyPem,
                publicKeyPem,
                Status.ACTIVE,
                Instant.now(),
                null,
                null,
                "system"
        );
    }

    public void markAsRotating() {
        this.status = Status.ROTATING;
        this.rotatedAt = Instant.now();
    }

    public void markAsRevoked() {
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isActiveOrRotating() {
        return status == Status.ACTIVE || status == Status.ROTATING;
    }

    public boolean isRevoked() {
        return status == Status.REVOKED;
    }

    public UUID getId() {
        return id;
    }

    public String getKid() {
        return kid;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
