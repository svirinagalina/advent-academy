package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serves stored assets.
 * <p>
 * Access follows the authorization matrix's published-or-content-authorized rule.
 * Assets included in the published snapshot are public. Newer assets remain private
 * until publication, even when the lesson already has an older published snapshot.
 * Private assets are readable by their uploader, the owner of the lesson's component,
 * and curators ({@code ADMIN}, {@code CONTENT_EDITOR}). Refusals are audited.
 */
@Service
public class DownloadAssetService {

    /** Roles that may read any asset, published or not. */
    private static final String[] CURATOR_ROLES = {"ADMIN", "CONTENT_EDITOR"};

    @org.springframework.beans.factory.annotation.Value("${asset.public-published:true}")
    private boolean publicPublished = true;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final AssetStorage assetStorage;
    private final LessonContentRepository lessonContentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrailPort auditTrail;
    private final ComponentRepository components;

    public DownloadAssetService(AssetStorage assetStorage,
                                LessonContentRepository lessonContentRepository,
                                CurrentUserProvider currentUserProvider,
                                AuditTrailPort auditTrail,
                                ComponentRepository components) {
        this.assetStorage = assetStorage;
        this.lessonContentRepository = lessonContentRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
        this.components = components;
    }

    @Transactional(readOnly = true)
    public StoredAsset getAssetById(UUID assetId) {
        var asset = assetStorage.findMetadataById(assetId)
                .orElseThrow(() -> new NotFoundException("Asset not found: " + assetId));
        requireDownloadable(asset);
        return assetStorage.findById(assetId).orElseThrow(() -> new NotFoundException("Asset not found"));
    }

    private void requireDownloadable(com.adventacademy.contentservice.domain.AssetMetadata asset) {
        if ((publicPublished && isPublished(asset)) || currentUserProvider.hasAnyRole(CURATOR_ROLES)) {
            return;
        }
        UUID currentUserId = currentUserProvider.currentUserId().orElse(null);
        if (currentUserId != null && (currentUserId.equals(asset.ownerId())
                || components.findByLessonId(asset.lessonId())
                        .map(component -> currentUserId.equals(component.getOwnerId())).orElse(false))) {
            return;
        }
        if (currentUserId != null && isPublished(asset) && learnerMayRead(currentUserId, asset.lessonId())) { return; }
        auditTrail.record(new AuditTrailPort.AuditEvent(
                "ASSET_ACCESS",
                currentUserId,
                "DOWNLOAD_ASSET",
                "asset:" + asset.id(),
                "DENIED",
                "asset belongs to unpublished content of another user"));
        throw new AssetAccessDeniedException(asset.id());
    }

    private boolean learnerMayRead(UUID user, UUID lesson) {
        return jdbc != null && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM content.lessons target
                  JOIN content.course_components cc ON cc.component_id=target.component_id
                  JOIN content.courses c ON c.id=cc.course_id AND c.status='PUBLISHED'
                  JOIN content.components module ON module.id=target.component_id AND module.status='PUBLISHED'
                  JOIN content.learning_enrollments e ON e.course_id=c.id AND e.user_id=?
                  WHERE target.id=? AND NOT EXISTS(
                    SELECT 1 FROM content.course_components previous_module
                    JOIN content.lessons previous ON previous.component_id=previous_module.component_id
                    WHERE previous_module.course_id=c.id
                    AND (previous_module.position<cc.position OR
                         (previous_module.position=cc.position AND previous.position<target.position))
                    AND NOT EXISTS(SELECT 1 FROM content.learning_completions done
                      WHERE done.user_id=e.user_id AND done.course_id=c.id AND done.lesson_id=previous.id)))
                """, Boolean.class, user, lesson));
    }

    private boolean isPublished(com.adventacademy.contentservice.domain.AssetMetadata asset) {
        return lessonContentRepository.findByLessonId(asset.lessonId())
                .map(content -> content.getStatus() == ContentStatus.PUBLISHED
                        && !asset.createdAt().isAfter(content.getUpdatedAt()))
                .orElse(false);
    }
}
