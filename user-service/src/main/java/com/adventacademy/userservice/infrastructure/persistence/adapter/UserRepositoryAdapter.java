package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RoleMapper;
import com.adventacademy.userservice.infrastructure.persistence.mapper.UserMapper;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;
    private final RoleMapper roleMapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, UserMapper mapper, RoleMapper roleMapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            return mapper.toDomain(jpaRepository.save(mapper.toEntity(user)));
        }

        Optional<UserEntity> existing = jpaRepository.findById(user.getId());
        if (existing.isEmpty()) {
            return mapper.toDomain(jpaRepository.save(mapper.toEntity(user)));
        }

        UserEntity entity = existing.get();
        applyScalarUpdates(entity, user);

        // Sync roles only when caller actually worked with them. Otherwise a service
        // that loads a user without roles (e.g. login updating lastLoginAt) would wipe
        // the user_roles join rows on save.
        boolean rolesLoaded = Hibernate.isInitialized(entity.getRoles());
        boolean userHasRoles = !user.getRoles().isEmpty();
        if (rolesLoaded || userHasRoles) {
            Set<RoleEntity> target = user.getRoles().stream()
                    .map(roleMapper::toEntity)
                    .collect(Collectors.toSet());
            entity.getRoles().clear();
            entity.getRoles().addAll(target);
        }

        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByIdWithRoles(UUID id) {
        return jpaRepository.findWithRolesById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmailVerificationToken(String token) {
        return jpaRepository.findByEmailVerificationToken(token)
                .map(mapper::toDomain);
    }

    @Override
    public List<User> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findAllWithRoles() {
        return jpaRepository.findAllWithRoles().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    private void applyScalarUpdates(UserEntity entity, User user) {
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setFirstName(user.getFirstName());
        entity.setLastName(user.getLastName());
        entity.setStatus(user.getStatus());
        entity.setEmailVerified(user.isEmailVerified());
        entity.setEmailVerificationToken(user.getEmailVerificationToken());
        entity.setMfaEnabled(user.isMfaEnabled());
        entity.setMfaSecret(user.getMfaSecret());
        entity.setFailedLoginAttempts(user.getFailedLoginAttempts());
        entity.setLockedUntil(user.getLockedUntil());
        entity.setLastLoginAt(user.getLastLoginAt());
    }
}
