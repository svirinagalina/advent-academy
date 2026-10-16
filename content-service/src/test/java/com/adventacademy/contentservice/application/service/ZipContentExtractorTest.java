package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.application.validation.AssetFileValidator;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.config.ArchiveUploadProperties;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.LessonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipContentExtractorTest {

    private final Map<UUID, StoredAsset> savedAssets = new HashMap<>();

    private final AssetStorage fakeAssetStorage = new AssetStorage() {
        @Override
        public StoredAsset save(StoredAsset asset) {
            savedAssets.put(asset.getId(), asset);
            return asset;
        }

        @Override
        public Optional<StoredAsset> findById(UUID id) {
            return Optional.ofNullable(savedAssets.get(id));
        }

        @Override
        public List<StoredAsset> findAllByLessonId(UUID lessonId) {
            throw new UnsupportedOperationException();
        }
    };

    private final LessonRepository fakeLessonRepository = new LessonRepository() {
        @Override
        public boolean existsById(UUID id) {
            return true;
        }
    };

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CurrentUserProvider currentUserProvider = () -> Optional.of(USER_ID);

    /** Every lesson in this test belongs to a component the uploading user owns. */
    private final ComponentRepository fakeComponentRepository = new ComponentRepository() {
        @Override
        public Optional<Component> findByLessonId(UUID lessonId) {
            return Optional.of(Component.create("Owning component", null, USER_ID, Clock.systemUTC()));
        }

        @Override
        public Component save(Component component) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Component> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Component> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Component> findAllById(Collection<UUID> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(UUID id) {
            throw new UnsupportedOperationException();
        }
    };

    private final ContentAccessGuard accessGuard =
            new ContentAccessGuard(currentUserProvider, event -> { });

    private UploadAssetService uploadAssetService;
    private ArchiveUploadProperties properties;
    private ZipContentExtractor extractor;

    @BeforeEach
    void setUp() {
        AssetFileValidator assetFileValidator = new AssetFileValidator(
                10 * 1024 * 1024,
                Set.of("image/png", "image/jpeg", "text/plain"));
        uploadAssetService = new UploadAssetService(assetFileValidator, fakeAssetStorage, fakeLessonRepository,
                fakeComponentRepository, accessGuard);

        properties = new ArchiveUploadProperties();
        properties.setMaxTotalUncompressedBytes(1024 * 1024);
        properties.setMaxEntryCount(10);

        extractor = new ZipContentExtractor(uploadAssetService, properties);
    }

    @Test
    void extractsMarkdownAndRewritesAssetReferences() throws IOException {
        UUID lessonId = UUID.randomUUID();
        byte[] zip = buildZip(Map.of(
                "lesson.md", "# Title\n\n![diagram](assets/diagram.png)".getBytes(StandardCharsets.UTF_8),
                "assets/diagram.png", java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
        ));

        String markdown = extractor.extract(zip, lessonId);

        assertThat(markdown).contains("# Title");
        assertThat(markdown).doesNotContain("assets/diagram.png");
        assertThat(markdown).containsPattern("asset:[0-9a-fA-F-]{36}");
        assertThat(savedAssets).hasSize(1);
        assertThat(savedAssets.values().iterator().next().getLessonId()).isEqualTo(lessonId);
    }

    @Test
    void rejectsArchiveWithoutMarkdown() throws IOException {
        byte[] zip = buildZip(Map.of("assets/diagram.png", "bytes".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> extractor.extract(zip, UUID.randomUUID()))
                .isInstanceOf(InvalidMarkdownFileException.class)
                .hasMessageContaining("must contain a Markdown file");
    }

    @Test
    void rejectsArchiveWithMultipleRootMarkdownFiles() throws IOException {
        byte[] zip = buildZip(Map.of(
                "lesson.md", "# One".getBytes(StandardCharsets.UTF_8),
                "extra.md", "# Two".getBytes(StandardCharsets.UTF_8)
        ));

        assertThatThrownBy(() -> extractor.extract(zip, UUID.randomUUID()))
                .isInstanceOf(InvalidMarkdownFileException.class)
                .hasMessageContaining("exactly one Markdown file");
    }

    @Test
    void rejectsZipSlipPath() throws IOException {
        byte[] zip = buildZip(Map.of(
                "lesson.md", "# Title".getBytes(StandardCharsets.UTF_8),
                "../../etc/passwd", "evil".getBytes(StandardCharsets.UTF_8)
        ));

        assertThatThrownBy(() -> extractor.extract(zip, UUID.randomUUID()))
                .isInstanceOf(InvalidMarkdownFileException.class)
                .hasMessageContaining("unsafe path");
    }

    @Test
    void rejectsArchiveExceedingEntryCountLimit() throws IOException {
        properties.setMaxEntryCount(1);
        byte[] zip = buildZip(Map.of(
                "lesson.md", "# Title".getBytes(StandardCharsets.UTF_8),
                "assets/one.png", "bytes".getBytes(StandardCharsets.UTF_8)
        ));

        assertThatThrownBy(() -> extractor.extract(zip, UUID.randomUUID()))
                .isInstanceOf(InvalidMarkdownFileException.class)
                .hasMessageContaining("too many files");
    }

    @Test
    void rejectsArchiveExceedingTotalUncompressedSizeLimit() throws IOException {
        properties.setMaxTotalUncompressedBytes(10);
        byte[] zip = buildZip(Map.of(
                "lesson.md", "# This markdown is longer than ten bytes".getBytes(StandardCharsets.UTF_8)
        ));

        assertThatThrownBy(() -> extractor.extract(zip, UUID.randomUUID()))
                .isInstanceOf(InvalidMarkdownFileException.class)
                .hasMessageContaining("uncompressed size limit");
    }

    private static byte[] buildZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}