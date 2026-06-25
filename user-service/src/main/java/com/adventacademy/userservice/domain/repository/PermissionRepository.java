package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.Permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository {

    Permission save(Permission permission);

    Optional<Permission> findById(UUID id);

    Optional<Permission> findByName(String name);

    List<Permission> findAll();

    void deleteById(UUID id);

    boolean existsByName(String name);
}