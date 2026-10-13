package com.adventacademy.contentservice.infrastructure.persistence;

import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostgresAssetStorageAdapterIT {

    @Autowired
    private AssetStorage assetStorage;

    @Test
    void saveAndFindById() {
        UUID lessonId = UUID.randomUUID();
        byte[] fileData = "test file content".getBytes();

        StoredAsset asset = StoredAsset.create(
                lessonId,
                "test.pdf",
                "application/pdf",
                fileData
        );

        assetStorage.save(asset);
        Optional<StoredAsset> found = assetStorage.findById(asset.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFilename()).isEqualTo("test.pdf");
        assertThat(found.get().getContentType()).isEqualTo("application/pdf");
        assertThat(found.get().getLessonId()).isEqualTo(lessonId);
        assertThat(found.get().getFileSize()).isEqualTo(fileData.length);
        assertThat(found.get().getFileData()).isEqualTo(fileData);
    }

    @Test
    void findAllByLessonId() {
        UUID lessonId = UUID.randomUUID();

        StoredAsset asset1 = StoredAsset.create(
                lessonId,
                "file1.pdf",
                "application/pdf",
                "content1".getBytes()
        );

        StoredAsset asset2 = StoredAsset.create(
                lessonId,
                "file2.png",
                "image/png",
                "content2".getBytes()
        );

        StoredAsset asset3 = StoredAsset.create(
                UUID.randomUUID(),
                "file3.pdf",
                "application/pdf",
                "content3".getBytes()
        );

        assetStorage.save(asset1);
        assetStorage.save(asset2);
        assetStorage.save(asset3);

        List<StoredAsset> found = assetStorage.findAllByLessonId(lessonId);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(StoredAsset::getFilename)
                .containsExactlyInAnyOrder("file1.pdf", "file2.png");
    }

    @Test
    void findByIdReturnsEmptyForNonExistentAsset() {
        Optional<StoredAsset> found = assetStorage.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void findAllByLessonIdReturnsEmptyListForNonExistentLesson() {
        List<StoredAsset> found = assetStorage.findAllByLessonId(UUID.randomUUID());
        assertThat(found).isEmpty();
    }
}
