package com.adventacademy.contentservice.domain.repository;

import com.adventacademy.contentservice.domain.LessonContent;

import java.util.Optional;
import java.util.UUID;

public interface LessonContentRepository {

    LessonContent save(LessonContent lessonContent);

    Optional<LessonContent> findById(UUID id);

    Optional<LessonContent> findByLessonId(UUID lessonId);

    void deleteById(UUID id);
}