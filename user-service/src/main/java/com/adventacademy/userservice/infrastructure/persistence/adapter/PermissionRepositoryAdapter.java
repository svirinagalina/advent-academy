package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.Permission;
import com.adventacademy.userservice.domain.repository.PermissionRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.PermissionEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.PermissionJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.PermissionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class PermissionRepositoryAdapter implements PermissionRepository {

    private final PermissionJpaRepository jpaRepository;
    private final PermissionMapper mapper;

    public PermissionRepositoryAdapter(PermissionJpaRepository jpaRepository, PermissionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Permission save(Permission permission) {
        PermissionEntity entity = mapper.toEntity(permission);
        PermissionEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Permission> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Permission> findByName(String name) {
        return jpaRepository.findByName(name)
                .map(mapper::toDomain);
    }

    @Override
    public List<Permission> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }
}
