package com.adventacademy.contentservice.domain.repository;

import com.adventacademy.contentservice.domain.constructor.Course;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface CourseRepository {

    Course save(Course course);

    Optional<Course> findById(UUID id);

    List<Course> findAll();

    boolean existsById(UUID id);

    /**
     * @return true if at least one course references the given component
     */
    boolean existsByComponentId(UUID componentId);

    void deleteById(UUID id);

    List<Course> findAllById(Collection<UUID> ids);
}
