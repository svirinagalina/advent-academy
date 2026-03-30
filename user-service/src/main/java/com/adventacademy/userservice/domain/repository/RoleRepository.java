package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {
    Role save(Role role);
    Optional<Role> findById(UUID id);
    Optional<Role> findByName(String name);
    List<Role> findAll();
    void deleteById(UUID id);
    boolean existsByName(String name);
}
