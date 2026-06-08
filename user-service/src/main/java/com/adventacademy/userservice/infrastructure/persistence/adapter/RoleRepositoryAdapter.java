package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RoleMapper;
import com.adventacademy.userservice.infrastructure.persistence.pagination.PaginationMapper;
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
public class RoleRepositoryAdapter implements RoleRepository {

    static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "createdAt");

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

    // простой список для RoleRepository.findAll()
    @Override
    public List<Role> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    // пагинированный метод из интерфейса
    @Override
    public PageResult<Role> findAll(PageRequest pageRequest) {
        Pageable pageable = PaginationMapper.toSpringPageable(pageRequest, SORTABLE_FIELDS);
        Page<UUID> idPage = jpaRepository.findIdsBy(pageable);
        long total = idPage.getTotalElements();
        if (idPage.isEmpty()) {
            return new PageResult<>(List.of(), pageRequest.page(), pageRequest.size(), total);
        }

        Sort sort = pageable.getSort();
        List<RoleEntity> entities = jpaRepository.findAllWithPermissionsByIdIn(idPage.getContent(), sort);
        Map<UUID, RoleEntity> byId = entities.stream()
                .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        List<Role> roles = idPage.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(mapper::toDomain)
                .toList();
        return new PageResult<>(roles, pageRequest.page(), pageRequest.size(), total);
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