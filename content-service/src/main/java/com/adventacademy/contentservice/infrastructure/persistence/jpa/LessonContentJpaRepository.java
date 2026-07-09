package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.infrastructure.persistence.entity.LessonContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonContentJpaRepository extends JpaRepository<LessonContentEntity, UUID> {
    Optional<LessonContentEntity> findByLessonId(UUID lessonId);
}
