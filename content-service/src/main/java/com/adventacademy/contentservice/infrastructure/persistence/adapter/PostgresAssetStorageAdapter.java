package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.infrastructure.persistence.entity.AssetEntity;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.AssetJpaRepository;
import com.adventacademy.contentservice.infrastructure.persistence.mapper.AssetMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresAssetStorageAdapter implements AssetStorage {

    private final AssetJpaRepository jpaRepository;
    private final AssetMapper mapper;

    public PostgresAssetStorageAdapter(AssetJpaRepository jpaRepository, AssetMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public StoredAsset save(StoredAsset asset) {
        AssetEntity entity = mapper.toEntity(asset);
        AssetEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<com.adventacademy.contentservice.domain.AssetMetadata> findMetadataById(UUID id) {
        return jpaRepository.findMetadataById(id);
    }

    @Override
    public Optional<StoredAsset> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<StoredAsset> findAllByLessonId(UUID lessonId) {
        return jpaRepository.findAllByLessonId(lessonId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
