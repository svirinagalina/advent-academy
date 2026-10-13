package com.adventacademy.userservice.infrastructure.persistence.mapper;

import com.adventacademy.userservice.domain.model.Permission;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RoleMapper {

    private final PermissionMapper permissionMapper;

    public RoleMapper(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    public Role toDomain(RoleEntity entity) {
        if (entity == null) {
            return null;
        }

        Set<Permission> permissions = Hibernate.isInitialized(entity.getPermissions())
                ? entity.getPermissions().stream()
                        .map(permissionMapper::toDomain)
                        .collect(Collectors.toSet())
                : new HashSet<>();

        return new Role(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                permissions,
                entity.isSystemRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public RoleEntity toEntity(Role domain) {
        if (domain == null) {
            return null;
        }

        RoleEntity entity = new RoleEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setPermissions(domain.getPermissions().stream()
                .map(permissionMapper::toEntity)
                .collect(Collectors.toSet()));
        entity.setSystemRole(domain.isSystemRole());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }
}
