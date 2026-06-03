package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByIdWithRoles(UUID id);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailVerificationToken(String token);
    List<User> findAll();
    List<User> findAllWithRoles();
    void deleteById(UUID id);
    boolean existsByEmail(String email);
}
