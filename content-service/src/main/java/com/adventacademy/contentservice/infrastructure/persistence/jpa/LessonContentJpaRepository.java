package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.infrastructure.persistence.entity.LessonContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonContentJpaRepository extends JpaRepository<LessonContentEntity, UUID> {
    Optional<LessonContentEntity> findByLessonId(UUID lessonId);

    @Query("select lc.lessonId from LessonContentEntity lc where lc.lessonId in :lessonIds and lc.status = :status")
    List<UUID> findLessonIdsByLessonIdInAndStatus(@Param("lessonIds") Collection<UUID> lessonIds,
                                                  @Param("status") ContentStatus status);
}
