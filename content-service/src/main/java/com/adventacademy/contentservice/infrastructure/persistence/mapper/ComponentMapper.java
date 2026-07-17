package com.adventacademy.contentservice.infrastructure.persistence.mapper;

import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.Lesson;
import com.adventacademy.contentservice.infrastructure.persistence.entity.ComponentEntity;
import com.adventacademy.contentservice.infrastructure.persistence.entity.LessonEntity;

import java.util.List;

@org.springframework.stereotype.Component
public class ComponentMapper {

    public ComponentEntity toEntity(Component component) {
        if (component == null) {
            return null;
        }
        ComponentEntity entity = new ComponentEntity();
        entity.setId(component.getId());
        entity.setTitle(component.getTitle());
        entity.setDescription(component.getDescription());
        entity.setStatus(component.getStatus());
        entity.setVersion(component.getVersion());
        entity.setCreatedAt(component.getCreatedAt());
        entity.setUpdatedAt(component.getUpdatedAt());
        for (Lesson lesson : component.getLessons()) {
            LessonEntity lessonEntity = new LessonEntity();
            lessonEntity.setId(lesson.getId());
            lessonEntity.setTitle(lesson.getTitle());
            lessonEntity.setPosition(lesson.getPosition());
            lessonEntity.setComponent(entity);
            entity.getLessons().add(lessonEntity);
        }
        return entity;
    }

    public Component toDomain(ComponentEntity entity) {
        if (entity == null) {
            return null;
        }
        List<Lesson> lessons = entity.getLessons().stream()
                .map(lessonEntity -> Lesson.restore(
                        lessonEntity.getId(),
                        lessonEntity.getTitle(),
                        lessonEntity.getPosition(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()))
                .toList();
        return Component.restore(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                lessons,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}