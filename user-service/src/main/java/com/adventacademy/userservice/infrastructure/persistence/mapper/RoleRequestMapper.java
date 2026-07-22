package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleRequestEntity;
import org.springframework.stereotype.Component;

@Component
public class RoleRequestMapper {

    public RoleRequestEntity toEntity(RoleRequest roleRequest) {
        if (roleRequest == null) {
            return null;
        }

        RoleRequestEntity entity = new RoleRequestEntity();
        entity.setId(roleRequest.getId());
        entity.setUserId(roleRequest.getUserId());
        entity.setRequestedRoleId(roleRequest.getRequestedRoleId());
        entity.setStatus(roleRequest.getStatus());
        entity.setReviewerId(roleRequest.getReviewerId());
        entity.setDecisionReason(roleRequest.getDecisionReason());
        entity.setCreatedAt(roleRequest.getCreatedAt());
        entity.setReviewedAt(roleRequest.getReviewedAt());
        entity.setUpdatedAt(roleRequest.getUpdatedAt());
        return entity;
    }

    public RoleRequest toDomain(RoleRequestEntity entity) {
        if (entity == null) {
            return null;
        }

        return RoleRequest.restore(
                entity.getId(),
                entity.getUserId(),
                entity.getRequestedRoleId(),
                entity.getStatus(),
                entity.getReviewerId(),
                entity.getDecisionReason(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getUpdatedAt());
    }
}
