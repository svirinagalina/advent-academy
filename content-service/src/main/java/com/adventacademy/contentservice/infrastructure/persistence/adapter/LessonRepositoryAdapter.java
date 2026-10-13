package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.repository.LessonRepository;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.LessonJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class LessonRepositoryAdapter implements LessonRepository {

    private final LessonJpaRepository jpaRepository;

    public LessonRepositoryAdapter(LessonJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsById(UUID lessonId) {
        return jpaRepository.existsById(lessonId);
    }
}
