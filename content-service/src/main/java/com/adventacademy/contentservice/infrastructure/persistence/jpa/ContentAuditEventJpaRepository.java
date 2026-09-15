package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.infrastructure.persistence.entity.ContentAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentAuditEventJpaRepository extends JpaRepository<ContentAuditEventEntity, UUID> {
}
