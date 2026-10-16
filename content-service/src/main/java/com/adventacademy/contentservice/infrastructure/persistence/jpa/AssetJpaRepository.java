package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.infrastructure.persistence.entity.AssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetJpaRepository extends JpaRepository<AssetEntity, UUID> {

    List<AssetEntity> findAllByLessonId(UUID lessonId);

    @org.springframework.data.jpa.repository.Query("select new com.adventacademy.contentservice.domain.AssetMetadata("
            + "a.id,a.lessonId,a.ownerId,a.filename,a.contentType,a.fileSize,a.createdAt,a.updatedAt) from AssetEntity a where a.id=:id")
    java.util.Optional<com.adventacademy.contentservice.domain.AssetMetadata> findMetadataById(
            @org.springframework.data.repository.query.Param("id") UUID id);
}
