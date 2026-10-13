package com.adventacademy.contentservice.infrastructure.persistence.mapper;

import com.adventacademy.contentservice.domain.constructor.CourseRef;
import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.infrastructure.persistence.entity.CourseRefEmbeddable;
import com.adventacademy.contentservice.infrastructure.persistence.entity.DirectionEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DirectionMapper {

    public DirectionEntity toEntity(Direction direction) {
        if (direction == null) {
            return null;
        }
        DirectionEntity entity = new DirectionEntity();
        entity.setId(direction.getId());
        entity.setTitle(direction.getTitle());
        entity.setDescription(direction.getDescription());
        entity.setOwnerId(direction.getOwnerId());
        entity.setVersion(direction.getVersion());
        entity.setCreatedAt(direction.getCreatedAt());
        entity.setUpdatedAt(direction.getUpdatedAt());
        for (CourseRef ref : direction.getCourses()) {
            entity.getCourses().add(new CourseRefEmbeddable(ref.getCourseId(), ref.getPosition()));
        }
        return entity;
    }

    public Direction toDomain(DirectionEntity entity) {
        if (entity == null) {
            return null;
        }
        List<CourseRef> refs = entity.getCourses().stream()
                .map(e -> CourseRef.restore(e.getCourseId(), e.getPosition()))
                .toList();
        return Direction.restore(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getOwnerId(),
                refs,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}
