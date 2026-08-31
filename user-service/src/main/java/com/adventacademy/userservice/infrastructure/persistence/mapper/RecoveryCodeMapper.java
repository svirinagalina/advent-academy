package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.RecoveryCode;
import com.adventacademy.userservice.infrastructure.persistence.entity.RecoveryCodeEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class RecoveryCodeMapper {

    public RecoveryCodeEntity toEntity(RecoveryCode code) {
        if (code == null) {
            return null;
        }
        RecoveryCodeEntity entity = new RecoveryCodeEntity();
        entity.setId(code.getId());
        entity.setCodeHash(code.getCodeHash());
        entity.setUsedAt(code.getUsedAt());
        entity.setCreatedAt(code.getCreatedAt());
        return entity;
    }

    public RecoveryCode toDomain(RecoveryCodeEntity entity) {
        if (entity == null) {
            return null;
        }
        return RecoveryCode.restore(
                entity.getId(),
                entity.getUser().getId(),
                entity.getCodeHash(),
                entity.getCreatedAt(),
                entity.getUsedAt()
        );
    }

    public void updateEntity(RecoveryCode code, RecoveryCodeEntity entity) {
        if (code == null || entity == null) {
            return;
        }
        entity.setCodeHash(code.getCodeHash());
        entity.setUsedAt(code.getUsedAt());
    }
}
