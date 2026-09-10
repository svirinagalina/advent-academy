package com.adventacademy.contentservice.domain.repository;

import com.adventacademy.contentservice.domain.constructor.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComponentRepository {
    Component save(Component component);
    Optional<Component> findById(UUID id);

    /**
     * @return the component the given lesson belongs to; lessons live inside the component
     * aggregate, so this is how content and asset uploads find the object that owns them.
     */
    Optional<Component> findByLessonId(UUID lessonId);
    List<Component> findAll();
    List<Component> findAllById(Collection<UUID> ids);
    boolean existsById(UUID id);
    void deleteById(UUID id);
}
