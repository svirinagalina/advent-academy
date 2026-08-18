package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DownloadAssetService {

    private final AssetStorage assetStorage;

    public DownloadAssetService(AssetStorage assetStorage) {
        this.assetStorage = assetStorage;
    }

    @Transactional(readOnly = true)
    public StoredAsset getAssetById(UUID assetId) {
        return assetStorage.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Asset not found: " + assetId));
    }
}
