package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByIdForUpdate(UUID id);
    Optional<User> findByIdWithRoles(UUID id);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailForUpdate(String email);
    Optional<User> findByEmailVerificationToken(String token);
    PageResult<User> findAll(PageRequest pageRequest);
    void deleteById(UUID id);
    boolean existsByEmail(String email);
}
