package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.infrastructure.persistence.entity.LessonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LessonJpaRepository extends JpaRepository<LessonEntity, UUID> {
}
