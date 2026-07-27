package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoleRequestJpaRepository extends JpaRepository<RoleRequestEntity, UUID> {
    Page<RoleRequestEntity> findByUserId(UUID userId, Pageable pageable);
    boolean existsByUserIdAndRequestedRoleIdAndStatus(UUID userId, UUID requestedRoleId, RoleRequestStatus status);
}
