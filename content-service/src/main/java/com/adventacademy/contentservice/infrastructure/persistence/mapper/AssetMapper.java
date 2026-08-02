package com.adventacademy.contentservice.infrastructure.persistence.mapper;

import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.infrastructure.persistence.entity.AssetEntity;
import org.springframework.stereotype.Component;

@Component
public class AssetMapper {

    public AssetEntity toEntity(StoredAsset asset) {
        if (asset == null) {
            return null;
        }
        AssetEntity entity = new AssetEntity();
        entity.setId(asset.getId());
        entity.setLessonId(asset.getLessonId());
        entity.setFilename(asset.getFilename());
        entity.setContentType(asset.getContentType());
        entity.setFileSize(asset.getFileSize());
        entity.setFileData(asset.getFileData());
        entity.setCreatedAt(asset.getCreatedAt());
        entity.setUpdatedAt(asset.getUpdatedAt());
        return entity;
    }

    public StoredAsset toDomain(AssetEntity entity) {
        if (entity == null) {
            return null;
        }
        return StoredAsset.restore(
                entity.getId(),
                entity.getLessonId(),
                entity.getFilename(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getFileData(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
