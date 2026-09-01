package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.RsaKeyPair;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RsaKeyRepository {
    void save(RsaKeyPair keyPair);

    Optional<RsaKeyPair> findByKid(String kid);

    List<RsaKeyPair> findActiveOrRotating();

    List<RsaKeyPair> findActive();

    Optional<RsaKeyPair> findById(UUID id);

    List<RsaKeyPair> findByStatus(RsaKeyPair.Status status);
}
