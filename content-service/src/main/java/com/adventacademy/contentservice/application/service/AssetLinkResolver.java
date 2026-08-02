package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.repository.AssetStorage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AssetLinkResolver {

    private static final Pattern ASSET_LINK_PATTERN = Pattern.compile(
            "asset:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private final AssetStorage assetStorage;

    public AssetLinkResolver(AssetStorage assetStorage) {
        this.assetStorage = assetStorage;
    }

    public String resolve(String markdown, UUID lessonId) {
        Matcher matcher = ASSET_LINK_PATTERN.matcher(markdown);
        Set<UUID> invalidAssetIds = new LinkedHashSet<>();
        StringBuilder rewritten = new StringBuilder();

        while (matcher.find()) {
            UUID assetId = UUID.fromString(matcher.group(1));
            boolean belongsToLesson = assetStorage.findById(assetId)
                    .map(asset -> asset.getLessonId().equals(lessonId))
                    .orElse(false);

            if (belongsToLesson) {
                matcher.appendReplacement(rewritten, "/api/assets/" + assetId);
            } else {
                invalidAssetIds.add(assetId);
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(rewritten);

        if (!invalidAssetIds.isEmpty()) {
            throw new InvalidAssetLinkException(invalidAssetIds);
        }

        return rewritten.toString();
    }
}