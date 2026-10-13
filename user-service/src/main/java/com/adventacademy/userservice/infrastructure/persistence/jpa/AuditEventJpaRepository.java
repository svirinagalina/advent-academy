package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {
}