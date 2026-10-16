package com.adventacademy.contentservice.application.validation;

import com.adventacademy.contentservice.application.command.UploadAssetCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

class AssetFileValidatorTest {

    private AssetFileValidator validator;
    private static final long MAX_SIZE = 1024 * 1024; // 1 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    @BeforeEach
    void setUp() {
        validator = new AssetFileValidator(MAX_SIZE, ALLOWED_TYPES);
    }

    @Test
    void validatesCorrectFile() {
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "test.pdf",
                "application/pdf",
                "%PDF-1.7 content".getBytes()
        );

        assertThatCode(() -> validator.validate(command))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("file must not be empty");
    }

    @Test
    void rejectsEmptyContent() {
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "test.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("file must not be empty");
    }

    @Test
    void rejectsNullFilename() {
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                null,
                "application/pdf",
                "%PDF-1.7 content".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("filename must not be blank");
    }

    @Test
    void rejectsBlankFilename() {
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "  ",
                "application/pdf",
                "%PDF-1.7 content".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("filename must not be blank");
    }

    @Test
    void rejectsNullContentType() {
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "test.pdf",
                null,
                "%PDF-1.7 content".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("content type must not be blank");
    }

    @Test
    void rejectsBlankContentType() {
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "test.pdf",
                "  ",
                "%PDF-1.7 content".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("content type must not be blank");
    }

    @Test
    void rejectsUnsupportedContentType() {
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "test.exe",
                "application/x-msdownload",
                "%PDF-1.7 content".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("unsupported content type");
    }

    @Test
    void rejectsFileTooLarge() {
        byte[] largeContent = new byte[(int) (MAX_SIZE + 1)];
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "large.pdf",
                "application/pdf",
                largeContent
        );

        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(InvalidAssetFileException.class)
                .hasMessageContaining("file size")
                .hasMessageContaining("exceeds limit");
    }

    @Test
    void acceptsFileAtMaxSize() {
        byte[] content = new byte[(int) MAX_SIZE];
        System.arraycopy("%PDF-1.7".getBytes(), 0, content, 0, 8);
        UploadAssetCommand command = new UploadAssetCommand(
                UUID.randomUUID(),
                "max.pdf",
                "application/pdf",
                content
        );

        assertThatCode(() -> validator.validate(command))
                .doesNotThrowAnyException();
    }
}
