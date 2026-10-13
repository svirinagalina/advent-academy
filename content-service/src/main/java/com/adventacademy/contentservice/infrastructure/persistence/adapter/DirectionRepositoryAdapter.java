package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
import com.adventacademy.contentservice.infrastructure.persistence.entity.DirectionEntity;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.DirectionJpaRepository;
import com.adventacademy.contentservice.infrastructure.persistence.mapper.DirectionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DirectionRepositoryAdapter implements DirectionRepository {

    private final DirectionJpaRepository jpaRepository;
    private final DirectionMapper mapper;

    public DirectionRepositoryAdapter(DirectionJpaRepository jpaRepository, DirectionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Direction save(Direction direction) {
        DirectionEntity saved = jpaRepository.save(mapper.toEntity(direction));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Direction> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Direction> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public boolean existsByCourseId(UUID courseId) {
        return jpaRepository.existsByCourseId(courseId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
