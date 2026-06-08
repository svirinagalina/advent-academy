package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;
import com.adventacademy.userservice.domain.repository.UserRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RoleMapper;
import com.adventacademy.userservice.infrastructure.persistence.mapper.UserMapper;
import com.adventacademy.userservice.infrastructure.persistence.pagination.PaginationMapper;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "email", "firstName", "lastName", "status", "createdAt", "lastLoginAt");

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
    public PageResult<User> findAll(PageRequest pageRequest) {
        Pageable pageable = PaginationMapper.toSpringPageable(pageRequest, SORTABLE_FIELDS);
        Page<UUID> idPage = jpaRepository.findIdsBy(pageable);
        long total = idPage.getTotalElements();
        if (idPage.isEmpty()) {
            return new PageResult<>(List.of(), pageRequest.page(), pageRequest.size(), total);
        }

        Sort sort = pageable.getSort();
        List<UserEntity> entities = jpaRepository.findAllWithRolesByIdIn(idPage.getContent(), sort);
        Map<UUID, UserEntity> byId = entities.stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        List<User> users = idPage.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(mapper::toDomain)
                .toList();
        return new PageResult<>(users, pageRequest.page(), pageRequest.size(), total);
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
