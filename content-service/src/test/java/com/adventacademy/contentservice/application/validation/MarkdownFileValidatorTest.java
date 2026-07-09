package com.adventacademy.contentservice.application.validation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownFileValidatorTest {

    private final MarkdownFileValidator validator = new MarkdownFileValidator(1024);

    @Test
    void acceptsValidMarkdownFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }
    @Test
    void rejectsWrongExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.txt", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }
    @Test
    void rejectsWrongContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.txt", "application/pdf", "# Урок".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }
    @Test
    void rejectsTooLargeFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.txt", "text/markdown", new byte[2048]);
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }
    @Test
    void rejectsNonUtf8Content() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidMarkdownFileException.class);
    }}