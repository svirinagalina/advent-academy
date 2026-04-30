package com.adventacademy.userservice.domain.model;

import com.adventacademy.userservice.domain.exception.InvalidUserStateException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Role {
    private UUID id;
    private String name;
    private String description;
    private Set<Permission> permissions;
    private boolean systemRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Role() {
        this.id = UUID.randomUUID();
        this.permissions = new HashSet<>();
        this.systemRole = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Role(UUID id, String name, String description, Set<Permission> permissions,
                boolean systemRole, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.permissions = permissions != null ? permissions : new HashSet<>();
        this.systemRole = systemRole;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void addPermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }
        this.permissions.add(permission);
        this.updatedAt = LocalDateTime.now();
    }

    public void removePermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }
        this.permissions.remove(permission);
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasPermission(String permissionName) {
        if (permissionName == null || permissionName.isBlank()) {
            return false;
        }
        return permissions.stream()
                .anyMatch(p -> permissionName.equals(p.getName()));
    }

    public boolean canBeDeleted() {
        return !systemRole;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions != null ? permissions : new HashSet<>();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public void setSystemRole(boolean systemRole) {
        this.systemRole = systemRole;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
