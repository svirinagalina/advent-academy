package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownloadAssetServiceTest {
    private final Clock before = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final Clock after = Clock.offset(before, java.time.Duration.ofMinutes(1));
    private final UUID owner = UUID.randomUUID(), uploader = UUID.randomUUID();
    private final Component component = Component.create("Module", null, owner, before);
    private final UUID lesson = component.addLesson("Lesson", before).getId();
    private final StoredAsset asset = StoredAsset.create(lesson, uploader, "image.png", "image/png", new byte[]{1}, after);
    private final LessonContent published = LessonContent.restore(UUID.randomUUID(), lesson, "# Published", "<h1>Published</h1>",
            ContentStatus.PUBLISHED, before.instant(), before.instant());
    private UUID caller = owner;
    private Set<String> roles = Set.of("INSTRUCTOR");
    private boolean componentExists = true;
    private final List<AuditTrailPort.AuditEvent> audit = new ArrayList<>();
    private final ComponentRepository components = new ComponentRepository() {
        public Optional<Component> findByLessonId(UUID id) { return componentExists && id.equals(lesson) ? Optional.of(component) : Optional.empty(); }
        public Component save(Component value) { throw new UnsupportedOperationException(); }
        public Optional<Component> findById(UUID id) { throw new UnsupportedOperationException(); }
        public List<Component> findAll() { throw new UnsupportedOperationException(); }
        public List<Component> findAllById(Collection<UUID> ids) { throw new UnsupportedOperationException(); }
        public boolean existsById(UUID id) { throw new UnsupportedOperationException(); }
        public void deleteById(UUID id) { throw new UnsupportedOperationException(); }
    };
    private int bodyReads;
    private final AssetStorage assets = new AssetStorage() {
        public StoredAsset save(StoredAsset value) { throw new UnsupportedOperationException(); }
        public Optional<StoredAsset> findById(UUID id) { bodyReads++; return id.equals(asset.getId()) ? Optional.of(asset) : Optional.empty(); }
        public Optional<com.adventacademy.contentservice.domain.AssetMetadata> findMetadataById(UUID id) {
            return id.equals(asset.getId()) ? Optional.of(com.adventacademy.contentservice.domain.AssetMetadata.from(asset)) : Optional.empty();
        }
        public List<StoredAsset> findAllByLessonId(UUID id) { throw new UnsupportedOperationException(); }
    };
    private final LessonContentRepository contents = new LessonContentRepository() {
        public LessonContent save(LessonContent value) { throw new UnsupportedOperationException(); }
        public Optional<LessonContent> findById(UUID id) { throw new UnsupportedOperationException(); }
        public Optional<LessonContent> findByLessonId(UUID id) { return id.equals(lesson) ? Optional.of(published) : Optional.empty(); }
        public void deleteById(UUID id) { throw new UnsupportedOperationException(); }
    };
    private final DownloadAssetService service = new DownloadAssetService(assets, contents, new CurrentUserProvider() {
        public Optional<UUID> currentUserId() { return Optional.ofNullable(caller); }
        public Set<String> currentRoles() { return roles; }
    }, audit::add, components);

    @Test
    void unauthorizedReadNeverLoadsBinaryBody() {
        caller = null; roles = Set.of();
        assertThatThrownBy(() -> service.getAssetById(asset.getId())).isInstanceOf(AssetAccessDeniedException.class);
        assertThat(bodyReads).isZero();
        caller = owner; roles = Set.of("INSTRUCTOR");
        assertThat(service.getAssetById(asset.getId())).isEqualTo(asset);
        assertThat(bodyReads).isEqualTo(1);
    }

    @Test
    void lessonOwnerCanReadImageUploadedByAnotherUserAfterPublication() {
        assertThat(asset.getOwnerId()).isNotEqualTo(owner);
        assertThat(service.getAssetById(asset.getId())).isSameAs(asset);
        assertThat(audit).isEmpty();
    }

    @Test
    void unrelatedInstructorAnonymousAndMissingComponentDoNotGrantAccess() {
        caller = UUID.randomUUID();
        assertThatThrownBy(() -> service.getAssetById(asset.getId())).isInstanceOf(AssetAccessDeniedException.class);
        caller = null;
        assertThatThrownBy(() -> service.getAssetById(asset.getId())).isInstanceOf(AssetAccessDeniedException.class);
        caller = owner; componentExists = false;
        assertThatThrownBy(() -> service.getAssetById(asset.getId())).isInstanceOf(AssetAccessDeniedException.class);
        assertThat(audit).hasSize(3);
    }

    @Test
    void uploaderCuratorAndPublishedImageAccessRemainAvailable() {
        caller = uploader;
        assertThat(service.getAssetById(asset.getId())).isSameAs(asset);
        caller = UUID.randomUUID(); roles = Set.of("CONTENT_EDITOR");
        assertThat(service.getAssetById(asset.getId())).isSameAs(asset);
        caller = null; roles = Set.of();
        published.updateContent("# Published", "<h1>Published</h1>", after);
        assertThat(service.getAssetById(asset.getId())).isSameAs(asset);
    }
}
