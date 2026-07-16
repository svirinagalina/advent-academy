package com.adventacademy.contentservice.domain.repository;

import com.adventacademy.contentservice.domain.constructor.Direction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectionRepository {

    Direction save(Direction direction);

    Optional<Direction> findById(UUID id);

    List<Direction> findAll();

    boolean existsById(UUID id);

    /**
     * @return true if at least one direction references the given course
     */
    boolean existsByCourseId(UUID courseId);

    void deleteById(UUID id);
}
