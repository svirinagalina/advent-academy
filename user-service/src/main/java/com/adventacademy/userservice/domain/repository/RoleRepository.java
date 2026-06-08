package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

    // простой список всех ролей
    List<Role> findAll();

    // пагинированный поиск (дополнительно)
    PageResult<Role> findAll(PageRequest pageRequest);

    Optional<Role> findById(UUID id);

    Optional<Role> findByIdWithPermissions(UUID id);

    Optional<Role> findByName(String name);

    Role save(Role role);

    void deleteById(UUID id);

    boolean existsByName(String name);
}