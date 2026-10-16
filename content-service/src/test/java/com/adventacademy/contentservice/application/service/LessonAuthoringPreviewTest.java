package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import com.adventacademy.contentservice.domain.StoredAsset;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.infrastructure.markdown.CommonMarkMarkdownRenderer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real authoring/rendering boundary without PostgreSQL or mock security filters. */
class LessonAuthoringPreviewTest {
    private final UUID owner = UUID.randomUUID();
    private final Component component = Component.create("Module", null, owner, Clock.systemUTC());
    private final UUID lesson = component.addLesson("Lesson", Clock.systemUTC()).getId();
    private final ComponentRepository components = new ComponentRepository() {
        public Optional<Component> findByLessonId(UUID id) { return id.equals(lesson) ? Optional.of(component) : Optional.empty(); }
        public Component save(Component value) { throw new UnsupportedOperationException(); }
        public Optional<Component> findById(UUID id) { throw new UnsupportedOperationException(); }
        public List<Component> findAll() { throw new UnsupportedOperationException(); }
        public List<Component> findAllById(Collection<UUID> ids) { throw new UnsupportedOperationException(); }
        public boolean existsById(UUID id) { throw new UnsupportedOperationException(); }
        public void deleteById(UUID id) { throw new UnsupportedOperationException(); }
    };
    private final AssetStorage assets = new AssetStorage() {
        public StoredAsset save(StoredAsset value) { throw new UnsupportedOperationException(); }
        public Optional<StoredAsset> findById(UUID id) { return Optional.empty(); }
        public List<StoredAsset> findAllByLessonId(UUID id) { throw new UnsupportedOperationException(); }
    };
    private final LessonAuthoringService service = new LessonAuthoringService(null, components,
            new ContentAccessGuard(() -> Optional.of(owner), event -> { }),
            new MarkdownFileValidator(5*1024*1024), new AssetLinkResolver(assets), new CommonMarkMarkdownRenderer());

    @Test
    void emptyAndWhitespacePreviewKeepSourceAndReturnEmptyHtml() {
        for (String source : List.of("", " ", "\t\n\r")) {
            var preview = service.preview(lesson, source);
            assertThat(preview.markdown()).isEqualTo(source);
            assertThat(preview.html()).isEmpty();
        }
    }

    @Test
    void blankPreviewStillHasTheNormalSizeLimit() {
        assertThatThrownBy(() -> service.preview(lesson, " ".repeat(5*1024*1024+1)))
                .isInstanceOf(InvalidMarkdownFileException.class).hasMessageContaining("exceeds limit");
    }

    @Test
    void nonblankPreviewStillUsesTheServerRendererAndAttachmentChecks() {
        assertThat(service.preview(lesson, "# Text\n\n<script>alert(1)</script>").html())
                .contains("<h1>Text</h1>").doesNotContain("<script>");
        assertThatThrownBy(() -> service.preview(lesson, "![](asset:"+UUID.randomUUID()+")"))
                .isInstanceOf(InvalidAssetLinkException.class);
    }
}
