package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import com.adventacademy.contentservice.infrastructure.persistence.entity.LessonContentEntity;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.LessonContentJpaRepository;
import com.adventacademy.contentservice.infrastructure.persistence.mapper.LessonContentMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LessonContentRepositoryAdapter implements LessonContentRepository {
    private final LessonContentJpaRepository jpaRepository;
    private final LessonContentMapper mapper;


    public LessonContentRepositoryAdapter(LessonContentJpaRepository jpaRepository, LessonContentMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }


    @Override
    public LessonContent save(LessonContent lessonContent) {
        LessonContentEntity entity = mapper.toEntity(lessonContent);
        LessonContentEntity saved = jpaRepository.save(entity);
        return  mapper.toDomain(saved);
    }


    @Override
    public Optional<LessonContent> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<LessonContent> findByLessonId(UUID lessonId) {
        return jpaRepository.findByLessonId(lessonId).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);

    }
}
