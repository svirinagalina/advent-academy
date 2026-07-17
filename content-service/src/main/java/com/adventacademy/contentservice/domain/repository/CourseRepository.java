package com.adventacademy.contentservice.domain.repository;

import com.adventacademy.contentservice.domain.constructor.Course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Course save(Course course);

    Optional<Course> findById(UUID id);

    List<Course> findAll();

    List<Course> findAllById(Collection<UUID> ids);

    boolean existsById(UUID id);

    /**
     * @return true if at least one course references the given component
     */
    boolean existsByComponentId(UUID componentId);

    void deleteById(UUID id);
}
