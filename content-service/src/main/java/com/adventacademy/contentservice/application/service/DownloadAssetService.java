package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serves stored assets to authenticated callers.
 * <p>
 * Assets are never public: the endpoint requires a token, and while the lesson content they belong
 * to is not {@link ContentStatus#PUBLISHED} the asset is additionally restricted to its uploader
 * and to curators ({@code ADMIN}, {@code CONTENT_EDITOR}). Refusals are audited, so leaked asset
 * ids cannot be probed unnoticed.
 */
@Service
public class DownloadAssetService {

    /** Roles that may read any asset, published or not. */
    private static final String[] CURATOR_ROLES = {"ADMIN", "CONTENT_EDITOR"};

    private final AssetStorage assetStorage;
    private final LessonContentRepository lessonContentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrailPort auditTrail;

    public DownloadAssetService(AssetStorage assetStorage,
                                LessonContentRepository lessonContentRepository,
                                CurrentUserProvider currentUserProvider,
                                AuditTrailPort auditTrail) {
        this.assetStorage = assetStorage;
        this.lessonContentRepository = lessonContentRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    public StoredAsset getAssetById(UUID assetId) {
        StoredAsset asset = assetStorage.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Asset not found: " + assetId));
        requireDownloadable(asset);
        return asset;
    }

    private void requireDownloadable(StoredAsset asset) {
        if (isPublished(asset.getLessonId()) || currentUserProvider.hasAnyRole(CURATOR_ROLES)) {
            return;
        }
        UUID currentUserId = currentUserProvider.currentUserId().orElse(null);
        if (currentUserId != null && currentUserId.equals(asset.getOwnerId())) {
            return;
        }
        auditTrail.record(new AuditTrailPort.AuditEvent(
                "ASSET_ACCESS",
                currentUserId,
                "DOWNLOAD_ASSET",
                "asset:" + asset.getId(),
                "DENIED",
                "asset belongs to unpublished content of another user"));
        throw new AssetAccessDeniedException(asset.getId());
    }

    private boolean isPublished(UUID lessonId) {
        return lessonContentRepository.findByLessonId(lessonId)
                .map(content -> content.getStatus() == ContentStatus.PUBLISHED)
                .orElse(false);
    }
}
