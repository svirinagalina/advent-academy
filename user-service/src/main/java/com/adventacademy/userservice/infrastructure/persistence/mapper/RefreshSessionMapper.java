package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.RefreshSession;
import com.adventacademy.userservice.infrastructure.persistence.entity.RefreshSessionEntity;
import org.springframework.stereotype.Component;

@Component
public class RefreshSessionMapper {

    public RefreshSessionEntity toPersistence(RefreshSession domain) {
        return RefreshSessionEntity.from(domain);
    }

    public RefreshSession toDomain(RefreshSessionEntity entity) {
        return entity.toDomain();
    }
}
