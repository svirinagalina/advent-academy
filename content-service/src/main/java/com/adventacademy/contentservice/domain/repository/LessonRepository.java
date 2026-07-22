package com.adventacademy.contentservice.domain.repository;

import java.util.UUID;

/**
 * Port expressing the ownership rule "lesson content belongs to an existing lesson".
 * <p>
 * Lessons live inside the {@code Component} aggregate; content uploads reference a lesson
 * only by its id, so use-cases use this port to reject content for a lesson that does not
 * exist instead of silently persisting an orphaned upload.
 */
public interface LessonRepository {

    /** @return {@code true} if a lesson with the given id exists. */
    boolean existsById(UUID lessonId);
}
