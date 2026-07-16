package com.adventacademy.contentservice.domain.repository;

import com.adventacademy.contentservice.domain.constructor.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComponentRepository {
    Component save(Component component);
    Optional<Component> findById(UUID id);
    List<Component> findAll();
    boolean existsById(UUID id);
    void deleteById(UUID id);
}
