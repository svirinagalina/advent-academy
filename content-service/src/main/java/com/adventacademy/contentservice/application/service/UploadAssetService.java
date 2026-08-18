package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadAssetCommand;
import com.adventacademy.contentservice.application.validation.AssetFileValidator;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadAssetService {

    private final AssetFileValidator assetFileValidator;
    private final AssetStorage assetStorage;
    private final LessonRepository lessonRepository;

    public UploadAssetService(
            AssetFileValidator assetFileValidator,
            AssetStorage assetStorage,
            LessonRepository lessonRepository
    ) {
        this.assetFileValidator = assetFileValidator;
        this.assetStorage = assetStorage;
        this.lessonRepository = lessonRepository;
    }

    @Transactional
    public StoredAsset uploadAsset(UploadAssetCommand command) {
        assetFileValidator.validate(command);

        if (!lessonRepository.existsById(command.lessonId())) {
            throw new NotFoundException("Lesson not found: " + command.lessonId());
        }

        StoredAsset asset = StoredAsset.create(
                command.lessonId(),
                command.filename(),
                command.contentType(),
                command.content()
        );

        return assetStorage.save(asset);
    }
}
