package com.adventacademy.contentservice.application.service;

import java.util.UUID;

/**
 * Thrown when the current user tries to download an asset that belongs to content which is not
 * published yet and which they neither uploaded nor curate.
 */
public class AssetAccessDeniedException extends RuntimeException {

    private final UUID assetId;

    public AssetAccessDeniedException(UUID assetId) {
        super("Asset " + assetId + " belongs to unpublished content of another user");
        this.assetId = assetId;
    }

    public UUID getAssetId() {
        return assetId;
    }
}
