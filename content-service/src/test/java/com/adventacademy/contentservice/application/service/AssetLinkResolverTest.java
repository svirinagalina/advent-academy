package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetLinkResolverTest {

    private final Map<UUID, StoredAsset> assets = new HashMap<>();
    private final AssetStorage fakeStorage = new AssetStorage() {
        @Override
        public StoredAsset save(StoredAsset asset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredAsset> findById(UUID id) {
            return Optional.ofNullable(assets.get(id));
        }

        @Override
        public java.util.List<StoredAsset> findAllByLessonId(UUID lessonId) {
            throw new UnsupportedOperationException();
        }
    };

    private final AssetLinkResolver resolver = new AssetLinkResolver(fakeStorage);

    @Test
    void validatesCanonicalLinksFromPreviouslyImportedMarkdown() {
        UUID lesson = UUID.randomUUID();
        StoredAsset asset = StoredAsset.create(lesson, "x.png", "image/png", new byte[]{1});
        assets.put(asset.getId(), asset);
        String canonical = "![image](/api/assets/" + asset.getId() + ")";
        assertThat(resolver.resolve(canonical, lesson)).isEqualTo(canonical);
        assertThatThrownBy(() -> resolver.resolve(canonical, UUID.randomUUID()))
                .isInstanceOf(InvalidAssetLinkException.class);
        assertThat(resolver.resolve("![image](/api/backend/assets/" + asset.getId() + ")", lesson))
                .isEqualTo(canonical);
    }

    @Test
    void rewritesValidAssetLinkToDownloadUrl() {
        UUID lessonId = UUID.randomUUID();
        StoredAsset asset = StoredAsset.create(lessonId, "diagram.png", "image/png", new byte[]{1});
        assets.put(asset.getId(), asset);

        String markdown = "See ![diagram](asset:" + asset.getId() + ") below.";

        String result = resolver.resolve(markdown, lessonId);

        assertThat(result).isEqualTo("See ![diagram](/api/assets/" + asset.getId() + ") below.");
    }

    @Test
    void rejectsReferenceToNonExistentAsset() {
        UUID lessonId = UUID.randomUUID();
        UUID missingAssetId = UUID.randomUUID();
        String markdown = "See asset:" + missingAssetId + " here.";

        assertThatThrownBy(() -> resolver.resolve(markdown, lessonId))
                .isInstanceOf(InvalidAssetLinkException.class)
                .satisfies(e -> assertThat(((InvalidAssetLinkException) e).getInvalidAssetIds())
                        .containsExactly(missingAssetId.toString()));
    }

    @Test
    void rejectsMalformedAssetReference() {
        String markdown = "See ![diagram](asset:12345) below.";

        assertThatThrownBy(() -> resolver.resolve(markdown, UUID.randomUUID()))
                .isInstanceOf(InvalidAssetLinkException.class)
                .satisfies(e -> assertThat(((InvalidAssetLinkException) e).getInvalidAssetIds())
                        .containsExactly("12345"));
    }

    @Test
    void reportsEveryInvalidReferenceAtOnce() {
        UUID missingAssetId = UUID.randomUUID();
        String markdown = "asset:" + missingAssetId + " and asset:not-a-uuid";

        assertThatThrownBy(() -> resolver.resolve(markdown, UUID.randomUUID()))
                .isInstanceOf(InvalidAssetLinkException.class)
                .satisfies(e -> assertThat(((InvalidAssetLinkException) e).getInvalidAssetIds())
                        .containsExactlyInAnyOrder(missingAssetId.toString(), "not-a-uuid"));
    }

    @Test
    void ignoresTrailingPunctuationAfterAssetLink() {
        UUID lessonId = UUID.randomUUID();
        StoredAsset asset = StoredAsset.create(lessonId, "diagram.png", "image/png", new byte[]{1});
        assets.put(asset.getId(), asset);

        String result = resolver.resolve("See asset:" + asset.getId() + ".", lessonId);

        assertThat(result).isEqualTo("See /api/assets/" + asset.getId() + ".");
    }

    @Test
    void rejectsReferenceToAssetFromAnotherLesson() {
        UUID lessonId = UUID.randomUUID();
        UUID foreignLessonId = UUID.randomUUID();
        StoredAsset foreignAsset = StoredAsset.create(foreignLessonId, "file.pdf", "application/pdf", new byte[]{1});
        assets.put(foreignAsset.getId(), foreignAsset);

        String markdown = "See asset:" + foreignAsset.getId() + " here.";

        assertThatThrownBy(() -> resolver.resolve(markdown, lessonId))
                .isInstanceOf(InvalidAssetLinkException.class)
                .satisfies(e -> assertThat(((InvalidAssetLinkException) e).getInvalidAssetIds())
                        .containsExactly(foreignAsset.getId().toString()));
    }

    @Test
    void leavesMarkdownWithoutAssetLinksUnchanged() {
        String markdown = "Just plain text, no assets here.";

        String result = resolver.resolve(markdown, UUID.randomUUID());

        assertThat(result).isEqualTo(markdown);
    }
}