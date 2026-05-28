package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RoleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpaRepository;
    private final RoleMapper mapper;

    public RoleRepositoryAdapter(RoleJpaRepository jpaRepository, RoleMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Role save(Role role) {
        RoleEntity entity = mapper.toEntity(role);
        RoleEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Role> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Role> findByIdWithPermissions(UUID id) {
        return jpaRepository.findWithPermissionsById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Role> findByName(String name) {
        return jpaRepository.findByName(name)
                .map(mapper::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Role> findAllWithPermissions() {
        return jpaRepository.findAllWithPermissions().stream()
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
