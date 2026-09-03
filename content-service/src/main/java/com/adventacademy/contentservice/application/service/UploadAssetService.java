package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadAssetCommand;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.application.validation.AssetFileValidator;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class UploadAssetService {

    private final AssetFileValidator assetFileValidator;
    private final AssetStorage assetStorage;
    private final LessonRepository lessonRepository;
    private final CurrentUserProvider currentUserProvider;

    public UploadAssetService(
            AssetFileValidator assetFileValidator,
            AssetStorage assetStorage,
            LessonRepository lessonRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.assetFileValidator = assetFileValidator;
        this.assetStorage = assetStorage;
        this.lessonRepository = lessonRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public StoredAsset uploadAsset(UploadAssetCommand command) {
        assetFileValidator.validate(command);

        if (!lessonRepository.existsById(command.lessonId())) {
            throw new NotFoundException("Lesson not found: " + command.lessonId());
        }

        UUID ownerId = currentUserProvider.currentUserId().orElse(null);
        StoredAsset asset = StoredAsset.create(
                command.lessonId(),
                ownerId,
                command.filename(),
                command.contentType(),
                command.content(),
                Clock.systemUTC()
        );

        return assetStorage.save(asset);
    }
}
