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
        StoredAsset asset = assetStorage.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Asset not found: " + assetId));
        requireDownloadable(asset);
        return asset;
    }

    private void requireDownloadable(StoredAsset asset) {
        if (isPublished(asset) || currentUserProvider.hasAnyRole(CURATOR_ROLES)) {
            return;
        }
        UUID currentUserId = currentUserProvider.currentUserId().orElse(null);
        if (currentUserId != null && (currentUserId.equals(asset.getOwnerId())
                || components.findByLessonId(asset.getLessonId())
                        .map(component -> currentUserId.equals(component.getOwnerId())).orElse(false))) {
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

    private boolean isPublished(StoredAsset asset) {
        return lessonContentRepository.findByLessonId(asset.getLessonId())
                .map(content -> content.getStatus() == ContentStatus.PUBLISHED
                        && !asset.getCreatedAt().isAfter(content.getUpdatedAt()))
                .orElse(false);
    }
}
