package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadAssetCommand;
import com.adventacademy.contentservice.application.validation.AssetFileValidator;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Use-case: store an uploaded asset for a lesson.
 * <p>
 * Lessons live inside a component aggregate, so the upload is authorized against that component:
 * only its owner and content curators ({@code ADMIN}, {@code CONTENT_EDITOR}) may attach assets
 * to it, which keeps one {@code INSTRUCTOR} from planting files in somebody else's lesson.
 * Refusals are audited, and the uploader is recorded on the asset so unpublished material stays
 * readable to them alone.
 */
@Service
public class UploadAssetService {

    private static final String AUDIT_EVENT_TYPE = "ASSET_ACCESS";
    private static final String RESOURCE_TYPE = "Component";

    private final AssetFileValidator assetFileValidator;
    private final AssetStorage assetStorage;
    private final LessonRepository lessonRepository;
    private final ComponentRepository componentRepository;
    private final ContentAccessGuard accessGuard;

    public UploadAssetService(
            AssetFileValidator assetFileValidator,
            AssetStorage assetStorage,
            LessonRepository lessonRepository,
            ComponentRepository componentRepository,
            ContentAccessGuard accessGuard
    ) {
        this.assetFileValidator = assetFileValidator;
        this.assetStorage = assetStorage;
        this.lessonRepository = lessonRepository;
        this.componentRepository = componentRepository;
        this.accessGuard = accessGuard;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Value("${asset.upload.max-lesson-bytes:104857600}")
    private long maxLessonBytes = 104857600;

    @Transactional
    public StoredAsset uploadAsset(UploadAssetCommand command) {
        UUID ownerId = accessGuard.requireCurrentUserId();
        assetFileValidator.validate(command);

        if (!lessonRepository.existsById(command.lessonId())) {
            throw new NotFoundException("Lesson not found: " + command.lessonId());
        }
        requireOwnedLesson(command.lessonId());

        if (jdbc != null) {
            jdbc.queryForList("SELECT id FROM content.lessons WHERE id=? FOR UPDATE", command.lessonId());
            Long stored = jdbc.queryForObject("SELECT COALESCE(sum(file_size),0) FROM content.assets WHERE lesson_id=?", Long.class, command.lessonId());
            if (stored + command.sizeBytes() > maxLessonBytes) {
                throw new com.adventacademy.contentservice.application.validation.InvalidAssetFileException("lesson asset storage quota exceeded");
            }
        }
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

    private void requireOwnedLesson(UUID lessonId) {
        Component component = componentRepository.findByLessonId(lessonId).orElse(null);
        if (component != null) {
            accessGuard.requireCurator(AUDIT_EVENT_TYPE, RESOURCE_TYPE, component.getId(), component.getOwnerId(),
                    "UPLOAD_ASSET");
            return;
        }
        // A lesson always belongs to a component; if that link is missing there is nothing to
        // authorize against, so only curators may proceed.
        if (accessGuard.isCurator()) {
            return;
        }
        accessGuard.recordDenial(AUDIT_EVENT_TYPE, RESOURCE_TYPE, lessonId, "UPLOAD_ASSET",
                "lesson does not belong to any component");
        throw new ContentAccessDeniedException("Lesson", lessonId);
    }
}
