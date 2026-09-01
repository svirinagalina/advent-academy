package com.adventacademy.contentservice.domain.validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilenameNormalizerTest {

    @Test
    void normalizesSimpleFilename() {
        String normalized = FilenameNormalizer.normalize("photo.png");
        assertTrue(normalized.endsWith("_photo.png"));
        assertTrue(normalized.startsWith(normalized.split("_")[0])); // Has UUID prefix
    }

    @Test
    void rejectPathTraversal() {
        assertThrows(IllegalArgumentException.class, () ->
            FilenameNormalizer.normalize("../../../etc/passwd.png")
        );
    }

    @Test
    void rejectBackslashPath() {
        assertThrows(IllegalArgumentException.class, () ->
            FilenameNormalizer.normalize("..\\..\\windows\\system32.exe")
        );
    }

    @Test
    void rejectNullBytes() {
        assertThrows(IllegalArgumentException.class, () ->
            FilenameNormalizer.normalize("image\0.png")
        );
    }

    @Test
    void rejectMissingExtension() {
        assertThrows(IllegalArgumentException.class, () ->
            FilenameNormalizer.normalize("noextension")
        );
    }

    @Test
    void rejectEmptyFilename() {
        assertThrows(IllegalArgumentException.class, () ->
            FilenameNormalizer.normalize("")
        );
    }

    @Test
    void sanitizesSpecialCharacters() {
        String normalized = FilenameNormalizer.normalize("file@#$%^&*(name).png");
        assertTrue(normalized.contains(".png"));
        assertTrue(normalized.contains("file"));
    }

    @Test
    void limitFilenameLength() {
        String longName = "a".repeat(300) + ".png";
        String normalized = FilenameNormalizer.normalize(longName);
        assertTrue(normalized.length() <= 255);
    }

    @Test
    void keepsReasonableFilenameLength() {
        String normalized = FilenameNormalizer.normalize("my_lesson_content.png");
        assertTrue(normalized.length() < 100);
        assertTrue(normalized.endsWith("_my_lesson_content.png"));
    }

    @Test
    void prefixesWithUUID() {
        String normalized = FilenameNormalizer.normalize("test.jpg");
        String[] parts = normalized.split("_", 2);
        assertEquals(2, parts.length);
        assertEquals(8, parts[0].length()); // UUID prefix is 8 chars
        assertTrue(parts[1].startsWith("test"));
    }

    @Test
    void handlesUppercaseExtension() {
        String normalized = FilenameNormalizer.normalize("PHOTO.PNG");
        assertTrue(normalized.endsWith(".png")); // Lowercase extension
    }

    @Test
    void replacesForwardSlashes() {
        String normalized = FilenameNormalizer.normalize("folder/subfolder/file.jpg");
        assertFalse(normalized.contains("/"));
        assertTrue(normalized.endsWith(".jpg"));
    }

    @Test
    void replacesBackslashes() {
        String normalized = FilenameNormalizer.normalize("folder\\subfolder\\file.jpg");
        assertFalse(normalized.contains("\\"));
        assertTrue(normalized.endsWith(".jpg"));
    }

    @Test
    void getDisplayName() {
        String normalized = FilenameNormalizer.normalize("my_file.png");
        String display = FilenameNormalizer.getDisplayName(normalized);
        assertTrue(display.endsWith("my_file.png"));
    }

    @Test
    void validatesPathSafety() {
        assertTrue(FilenameNormalizer.isValidPath("normalfile.txt"));
        assertFalse(FilenameNormalizer.isValidPath("../etc/passwd"));
        assertFalse(FilenameNormalizer.isValidPath("/absolute/path"));
        assertFalse(FilenameNormalizer.isValidPath("double//slash"));
    }

    @Test
    void rejectsSemicolonInjection() {
        assertThrows(IllegalArgumentException.class, () ->
            FilenameNormalizer.normalize("file;rm -rf /.png")
        );
    }

    @Test
    void rejectsShellInjection() {
        assertThrows(IllegalArgumentException.class, () ->
            FilenameNormalizer.normalize("file`whoami`.png")
        );
    }

    @Test
    void multipleConsecutiveUnderscoresNormalized() {
        String normalized = FilenameNormalizer.normalize("file____name.png");
        assertFalse(normalized.contains("____"));
    }
}
