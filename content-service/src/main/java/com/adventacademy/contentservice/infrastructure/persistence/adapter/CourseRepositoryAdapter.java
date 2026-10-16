package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.infrastructure.persistence.entity.CourseEntity;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.CourseJpaRepository;
import com.adventacademy.contentservice.infrastructure.persistence.mapper.CourseMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseRepositoryAdapter implements CourseRepository {

    private final CourseJpaRepository jpaRepository;
    private final CourseMapper mapper;

    public CourseRepositoryAdapter(CourseJpaRepository jpaRepository, CourseMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Course save(Course course) {
        CourseEntity saved = jpaRepository.save(mapper.toEntity(course));
        return mapper.toDomain(saved);
    }

    @Override
    public List<Course> findAllById(Collection<UUID> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Course> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Course> findManagedByOwnerId(UUID ownerId) {
        return jpaRepository.findByOwnerId(ownerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Course> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public boolean existsByComponentId(UUID componentId) {
        return jpaRepository.existsByComponentId(componentId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Course> findAllVisibleByOwnerId(UUID ownerId) {
        return jpaRepository.findAllVisibleByOwnerId(ownerId).stream().map(mapper::toDomain).toList();
    }
}
