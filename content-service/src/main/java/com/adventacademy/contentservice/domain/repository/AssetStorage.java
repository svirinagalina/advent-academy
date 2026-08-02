package com.adventacademy.contentservice.domain.repository;

import com.adventacademy.contentservice.domain.StoredAsset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetStorage {

    StoredAsset save(StoredAsset asset);

    Optional<StoredAsset> findById(UUID id);

    List<StoredAsset> findAllByLessonId(UUID lessonId);
}
