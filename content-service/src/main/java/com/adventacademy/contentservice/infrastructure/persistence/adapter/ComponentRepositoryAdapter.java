package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.infrastructure.persistence.entity.ComponentEntity;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.ComponentJpaRepository;
import com.adventacademy.contentservice.infrastructure.persistence.mapper.ComponentMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ComponentRepositoryAdapter implements ComponentRepository {

    private final ComponentJpaRepository jpaRepository;
    private final ComponentMapper mapper;

    public ComponentRepositoryAdapter(ComponentJpaRepository jpaRepository, ComponentMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Component save(Component component) {
        ComponentEntity saved = jpaRepository.save(mapper.toEntity(component));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Component> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Component> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Component> findAllById(Collection<UUID> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}