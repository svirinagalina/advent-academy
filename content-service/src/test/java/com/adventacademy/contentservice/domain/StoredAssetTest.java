package com.adventacademy.contentservice.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoredAssetTest {

    @Test
    void createValidAsset() {
        UUID lessonId = UUID.randomUUID();
        byte[] fileData = "test content".getBytes();

        StoredAsset asset = StoredAsset.create(
                lessonId,
                "test.pdf",
                "application/pdf",
                fileData
        );

        assertThat(asset.getId()).isNotNull();
        assertThat(asset.getLessonId()).isEqualTo(lessonId);
        assertThat(asset.getFilename()).isEqualTo("test.pdf");
        assertThat(asset.getContentType()).isEqualTo("application/pdf");
        assertThat(asset.getFileSize()).isEqualTo(fileData.length);
        assertThat(asset.getFileData()).isEqualTo(fileData);
        assertThat(asset.getCreatedAt()).isNotNull();
        assertThat(asset.getUpdatedAt()).isNotNull();
    }

    @Test
    void createRejectsNullLessonId() {
        assertThatThrownBy(() ->
                StoredAsset.create(null, "test.pdf", "application/pdf", "content".getBytes())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lessonId must not be null");
    }

    @Test
    void createRejectsNullFilename() {
        assertThatThrownBy(() ->
                StoredAsset.create(UUID.randomUUID(), null, "application/pdf", "content".getBytes())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename must not be blank");
    }

    @Test
    void createRejectsBlankFilename() {
        assertThatThrownBy(() ->
                StoredAsset.create(UUID.randomUUID(), "  ", "application/pdf", "content".getBytes())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename must not be blank");
    }

    @Test
    void createRejectsNullContentType() {
        assertThatThrownBy(() ->
                StoredAsset.create(UUID.randomUUID(), "test.pdf", null, "content".getBytes())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentType must not be blank");
    }

    @Test
    void createRejectsBlankContentType() {
        assertThatThrownBy(() ->
                StoredAsset.create(UUID.randomUUID(), "test.pdf", "  ", "content".getBytes())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentType must not be blank");
    }

    @Test
    void createRejectsNullFileData() {
        assertThatThrownBy(() ->
                StoredAsset.create(UUID.randomUUID(), "test.pdf", "application/pdf", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileData must not be empty");
    }

    @Test
    void createRejectsEmptyFileData() {
        assertThatThrownBy(() ->
                StoredAsset.create(UUID.randomUUID(), "test.pdf", "application/pdf", new byte[0])
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileData must not be empty");
    }

    @Test
    void restoreCreatesAssetWithAllFields() {
        UUID id = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        byte[] fileData = "content".getBytes();

        StoredAsset asset = StoredAsset.restore(
                id,
                lessonId,
                "file.pdf",
                "application/pdf",
                fileData.length,
                fileData,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        assertThat(asset.getId()).isEqualTo(id);
        assertThat(asset.getLessonId()).isEqualTo(lessonId);
        assertThat(asset.getFilename()).isEqualTo("file.pdf");
        assertThat(asset.getContentType()).isEqualTo("application/pdf");
        assertThat(asset.getFileSize()).isEqualTo(fileData.length);
        assertThat(asset.getFileData()).isEqualTo(fileData);
    }
}
