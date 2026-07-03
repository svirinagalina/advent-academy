package com.adventacademy.contentservice.infrastructure.persistence;

import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.infrastructure.persistence.entity.LessonContentEntity;
import org.springframework.stereotype.Component;

@Component
public class LessonContentMapper {
    public LessonContentEntity mapToEntity(LessonContent lessonContent) {
        LessonContentEntity lessonContentEntity = new LessonContentEntity();
        lessonContentEntity.setLessonId(lessonContent.getLessonId());
        LessonContentEntity.setId
    }
}
