package com.adventacademy.userservice.infrastructure.persistence.entity;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rsa_keys", indexes = {
        @Index(name = "idx_kid", columnList = "kid", unique = true),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RsaKeyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String kid;

    @Column(nullable = false, length = 20)
    private String algorithm;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String privateKeyPem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RsaKeyPair.Status status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant rotatedAt;

    private Instant revokedAt;

    @Column(length = 255)
    private String createdBy;

    public static RsaKeyEntity from(RsaKeyPair domain) {
        return RsaKeyEntity.builder()
                .id(domain.getId())
                .kid(domain.getKid())
                .algorithm(domain.getAlgorithm())
                .privateKeyPem(domain.getPrivateKeyPem())
                .publicKeyPem(domain.getPublicKeyPem())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .rotatedAt(domain.getRotatedAt())
                .revokedAt(domain.getRevokedAt())
                .createdBy(domain.getCreatedBy())
                .build();
    }

    public RsaKeyPair toDomain() {
        return new RsaKeyPair(id, kid, algorithm, privateKeyPem, publicKeyPem, status, createdAt, rotatedAt, revokedAt, createdBy);
    }
}
