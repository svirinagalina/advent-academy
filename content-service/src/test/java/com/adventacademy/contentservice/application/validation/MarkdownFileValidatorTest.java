package com.adventacademy.contentservice.application.validation;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownFileValidatorTest {

    private final MarkdownFileValidator validator = new MarkdownFileValidator(1024);

    private static UploadLessonContentCommand command(String filename, String contentType, byte[] content) {
        return new UploadLessonContentCommand(UUID.randomUUID(), filename, contentType, content);
    }

    @Test
    void acceptsValidMarkdownFile() {
        UploadLessonContentCommand cmd = command(
                "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> validator.validate(cmd)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyFile() {
        UploadLessonContentCommand cmd = command("lesson.md", "text/markdown", new byte[0]);

        assertThatThrownBy(() -> validator.validate(cmd))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }

    @Test
    void rejectsWrongExtension() {
        UploadLessonContentCommand cmd = command(
                "lesson.txt", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(cmd))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }

    @Test
    void rejectsWrongContentType() {
        UploadLessonContentCommand cmd = command(
                "lesson.md", "application/pdf", "# Урок".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(cmd))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }

    @Test
    void rejectsTooLargeFile() {
        UploadLessonContentCommand cmd = command("lesson.md", "text/markdown", new byte[2048]);

        assertThatThrownBy(() -> validator.validate(cmd))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }

    @Test
    void rejectsNonUtf8Content() {
        UploadLessonContentCommand cmd = command(
                "lesson.md", "text/markdown", new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> validator.validate(cmd))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }
}
