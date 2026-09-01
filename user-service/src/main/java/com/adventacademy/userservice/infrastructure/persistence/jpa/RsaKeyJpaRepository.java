package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import com.adventacademy.userservice.infrastructure.persistence.entity.RsaKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RsaKeyJpaRepository extends JpaRepository<RsaKeyEntity, UUID> {
    Optional<RsaKeyEntity> findByKid(String kid);

    @Query("SELECT e FROM RsaKeyEntity e WHERE e.status IN ('ACTIVE', 'ROTATING') ORDER BY e.createdAt DESC")
    List<RsaKeyEntity> findActiveOrRotating();

    @Query("SELECT e FROM RsaKeyEntity e WHERE e.status = 'ACTIVE' ORDER BY e.createdAt DESC")
    List<RsaKeyEntity> findActive();

    List<RsaKeyEntity> findByStatus(RsaKeyPair.Status status);
}
