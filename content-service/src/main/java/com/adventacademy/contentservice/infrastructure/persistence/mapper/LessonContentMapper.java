package com.adventacademy.contentservice.infrastructure.persistence.mapper;

import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.infrastructure.persistence.entity.LessonContentEntity;
import org.springframework.stereotype.Component;

@Component
public class LessonContentMapper {
    public LessonContentEntity toEntity(LessonContent lessonContent) {
        if (lessonContent == null) {
            return null;
        }
        LessonContentEntity lessonContentEntity = new LessonContentEntity();
        lessonContentEntity.setLessonId(lessonContent.getLessonId());
        lessonContentEntity.setId(lessonContent.getId());
        lessonContentEntity.setHtml(lessonContent.getHtml());
        lessonContentEntity.setCreatedAt(lessonContent.getCreatedAt());
        lessonContentEntity.setUpdatedAt(lessonContent.getUpdatedAt());
        lessonContentEntity.setMarkdown(lessonContent.getMarkdown());
        lessonContentEntity.setStatus(lessonContent.getStatus());
        return lessonContentEntity;
    }

    public LessonContent toDomain(LessonContentEntity entity) {
        if (entity == null) {
            return null;
        }
        return LessonContent.restore(
                entity.getId(),
                entity.getLessonId(),
                entity.getMarkdown(),
                entity.getHtml(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}