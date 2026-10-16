package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.repository.AssetStorage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites {@code asset:<uuid>} references in lesson Markdown into
 * {@code /api/assets/<uuid>} download links.
 * <p>
 * A reference is accepted only when it is a well-formed UUID, the asset exists
 * and it belongs to the lesson being uploaded. Every rejected reference is
 * collected and reported at once through {@link InvalidAssetLinkException},
 * so the author gets the full list instead of one error at a time.
 */
@Component
public class AssetLinkResolver {

    /**
     * Deliberately wider than a UUID: a malformed reference such as
     * {@code asset:12345} must be reported rather than silently left in the
     * Markdown. Restricting the character class to UUID characters keeps
     * trailing prose punctuation (e.g. {@code asset:<uuid>.}) out of the match.
     */
    private static final Pattern ASSET_LINK_PATTERN = Pattern.compile("(?:asset:|/api/(?:backend/)?assets/)([A-Za-z0-9-]*)");

    private static final Pattern ASSET_ID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final AssetStorage assetStorage;

    public AssetLinkResolver(AssetStorage assetStorage) {
        this.assetStorage = assetStorage;
    }

    public String resolve(String markdown, UUID lessonId) {
        Matcher matcher = ASSET_LINK_PATTERN.matcher(markdown);
        Set<String> invalidAssetIds = new LinkedHashSet<>();
        StringBuilder rewritten = new StringBuilder();

        while (matcher.find()) {
            String reference = matcher.group(1);

            if (isUsableAsset(reference, lessonId)) {
                matcher.appendReplacement(rewritten, "/api/assets/" + reference);
            } else {
                invalidAssetIds.add(reference.isEmpty() ? matcher.group() : reference);
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(rewritten);

        if (!invalidAssetIds.isEmpty()) {
            throw new InvalidAssetLinkException(invalidAssetIds);
        }

        return rewritten.toString();
    }

    private boolean isUsableAsset(String reference, UUID lessonId) {
        if (!ASSET_ID_PATTERN.matcher(reference).matches()) {
            return false;
        }
        return assetStorage.findMetadataById(UUID.fromString(reference))
                .map(asset -> asset.lessonId().equals(lessonId))
                .orElse(false);
    }
}
