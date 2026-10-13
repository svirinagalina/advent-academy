package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.infrastructure.persistence.entity.AuditEventEntity;
import org.springframework.stereotype.Component;

@Component
public class AuditEventMapper {

    public AuditEventEntity toEntity(AuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setId(event.getId());
        entity.setEventType(event.getEventType().name());
        entity.setActorUserId(event.getActorUserId());
        entity.setTargetUserId(event.getTargetUserId());
        entity.setTimestamp(event.getTimestamp());
        entity.setIpAddress(event.getIpAddress());
        entity.setUserAgent(event.getUserAgent());
        entity.setResult(event.getResult().name());
        entity.setReason(event.getReason());
        entity.setMetadata(event.getMetadata());
        return entity;
    }

    public AuditEvent toDomain(AuditEventEntity entity) {
        return new AuditEvent(
                entity.getId(),
                AuditEventType.valueOf(entity.getEventType()),
                entity.getActorUserId(),
                entity.getTargetUserId(),
                entity.getTimestamp(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                AuditResult.valueOf(entity.getResult()),
                entity.getReason(),
                entity.getMetadata()
        );
    }
}