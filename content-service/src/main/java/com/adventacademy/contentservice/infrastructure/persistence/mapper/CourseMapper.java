package com.adventacademy.contentservice.infrastructure.persistence.mapper;

import com.adventacademy.contentservice.domain.constructor.ComponentRef;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.infrastructure.persistence.entity.ComponentRefEmbeddable;
import com.adventacademy.contentservice.infrastructure.persistence.entity.CourseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CourseMapper {

    public CourseEntity toEntity(Course course) {
        if (course == null) {
            return null;
        }
        CourseEntity entity = new CourseEntity();
        entity.setId(course.getId());
        entity.setTitle(course.getTitle());
        entity.setDescription(course.getDescription());
        entity.setStatus(course.getStatus());
        entity.setOwnerId(course.getOwnerId());
        entity.setVersion(course.getVersion());
        entity.setCreatedAt(course.getCreatedAt());
        entity.setUpdatedAt(course.getUpdatedAt());
        for (ComponentRef ref : course.getComponents()) {
            entity.getComponents().add(new ComponentRefEmbeddable(ref.getComponentId(), ref.getPosition()));
        }
        return entity;
    }

    public Course toDomain(CourseEntity entity) {
        if (entity == null) {
            return null;
        }
        List<ComponentRef> refs = entity.getComponents().stream()
                .map(e -> ComponentRef.restore(e.getComponentId(), e.getPosition()))
                .toList();
        return Course.restore(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getOwnerId(),
                refs,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}
