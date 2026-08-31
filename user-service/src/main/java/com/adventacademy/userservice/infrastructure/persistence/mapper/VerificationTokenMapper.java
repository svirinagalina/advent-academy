package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.infrastructure.persistence.entity.VerificationTokenEntity;
import org.springframework.stereotype.Component;

@Component
public class VerificationTokenMapper {

    public VerificationTokenEntity toEntity(VerificationToken token) {
        if (token == null) return null;
        var entity = new VerificationTokenEntity();
        entity.setId(token.getId());
        entity.setTokenDigest(token.getTokenDigest());
        entity.setPurpose(token.getPurpose());
        entity.setCreatedAt(token.getCreatedAt());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setUsedAt(token.getUsedAt());
        return entity;
    }

    public VerificationToken toDomain(VerificationTokenEntity entity) {
        if (entity == null) return null;
        return VerificationToken.restore(
                entity.getId(),
                entity.getUser().getId(),
                entity.getTokenDigest(),
                entity.getPurpose(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getUsedAt()
        );
    }
}
