package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import com.adventacademy.contentservice.infrastructure.markdown.CommonMarkMarkdownRenderer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownPreviewServiceTest {

    private final MarkdownFileValidator validator = new MarkdownFileValidator(1024);
    private final MarkdownRenderer renderer = new CommonMarkMarkdownRenderer();
    private final MarkdownPreviewService service = new MarkdownPreviewService(validator, renderer);

    private static UploadLessonContentCommand command(String filename, String contentType, byte[] content) {
        return new UploadLessonContentCommand(null, filename, contentType, content);
    }

    @Test
    void rendersValidMarkdownWithNoLessonContext() {
        UploadLessonContentCommand cmd = command(
                "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        MarkdownPreviewResult result = service.preview(cmd);

        assertThat(result.markdown()).isEqualTo("# Урок");
        assertThat(result.html()).contains("<h1>");
    }

    @Test
    void rejectsZipFileEvenIfOtherwiseValid() {
        UploadLessonContentCommand cmd = command(
                "bundle.zip", "application/zip", "not-really-a-zip".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.preview(cmd))
                .isInstanceOf(InvalidMarkdownFileException.class)
                .hasMessageContaining(".md or .markdown");
    }

    @Test
    void propagatesValidatorRejectionForWrongExtension() {
        UploadLessonContentCommand cmd = command(
                "lesson.txt", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.preview(cmd))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }
}
