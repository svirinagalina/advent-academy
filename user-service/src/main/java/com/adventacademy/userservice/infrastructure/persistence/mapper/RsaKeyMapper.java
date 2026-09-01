package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import com.adventacademy.userservice.infrastructure.persistence.entity.RsaKeyEntity;
import org.springframework.stereotype.Component;

@Component
public class RsaKeyMapper {
    public RsaKeyEntity toPersistence(RsaKeyPair domain) {
        return RsaKeyEntity.from(domain);
    }

    public RsaKeyPair toDomain(RsaKeyEntity entity) {
        return entity.toDomain();
    }
}
