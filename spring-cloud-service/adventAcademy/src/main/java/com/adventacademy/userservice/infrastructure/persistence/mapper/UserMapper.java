package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    private final RoleMapper roleMapper;

    public UserMapper(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getStatus(),
                entity.getRoles().stream()
                        .map(roleMapper::toDomain)
                        .collect(Collectors.toSet()),
                entity.isEmailVerified(),
                entity.getEmailVerificationToken(),
                entity.isMfaEnabled(),
                entity.getMfaSecret(),
                entity.getFailedLoginAttempts(),
                entity.getLockedUntil(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public UserEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }

        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setEmail(domain.getEmail());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setFirstName(domain.getFirstName());
        entity.setLastName(domain.getLastName());
        entity.setStatus(domain.getStatus());
        entity.setRoles(domain.getRoles().stream()
                .map(roleMapper::toEntity)
                .collect(Collectors.toSet()));
        entity.setEmailVerified(domain.isEmailVerified());
        entity.setEmailVerificationToken(domain.getEmailVerificationToken());
        entity.setMfaEnabled(domain.isMfaEnabled());
        entity.setMfaSecret(domain.getMfaSecret());
        entity.setFailedLoginAttempts(domain.getFailedLoginAttempts());
        entity.setLockedUntil(domain.getLockedUntil());
        entity.setLastLoginAt(domain.getLastLoginAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }
}
