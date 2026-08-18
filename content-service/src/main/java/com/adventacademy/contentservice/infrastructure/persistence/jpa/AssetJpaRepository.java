package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.infrastructure.persistence.entity.AssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetJpaRepository extends JpaRepository<AssetEntity, UUID> {

    List<AssetEntity> findAllByLessonId(UUID lessonId);
}
