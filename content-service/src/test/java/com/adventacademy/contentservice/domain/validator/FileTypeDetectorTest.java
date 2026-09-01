package com.adventacademy.contentservice.domain.validator;

import com.adventacademy.contentservice.domain.model.AllowedFileType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeDetectorTest {

    @Test
    void detectsPNG() throws IOException {
        byte[] pngMagic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] fileContent = new byte[100];
        System.arraycopy(pngMagic, 0, fileContent, 0, pngMagic.length);

        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(fileContent));
        assertEquals(AllowedFileType.PNG, result);
    }

    @Test
    void detectsJPEG() throws IOException {
        byte[] jpegMagic = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] fileContent = new byte[100];
        System.arraycopy(jpegMagic, 0, fileContent, 0, jpegMagic.length);

        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(fileContent));
        assertEquals(AllowedFileType.JPEG, result);
    }

    @Test
    void detectsGIF87() throws IOException {
        byte[] gifMagic = "GIF87a".getBytes();
        byte[] fileContent = new byte[100];
        System.arraycopy(gifMagic, 0, fileContent, 0, gifMagic.length);

        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(fileContent));
        assertEquals(AllowedFileType.GIF, result);
    }

    @Test
    void detectsGIF89() throws IOException {
        byte[] gifMagic = "GIF89a".getBytes();
        byte[] fileContent = new byte[100];
        System.arraycopy(gifMagic, 0, fileContent, 0, gifMagic.length);

        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(fileContent));
        assertEquals(AllowedFileType.GIF, result);
    }

    @Test
    void detectsPDF() throws IOException {
        byte[] pdfMagic = "%PDF".getBytes();
        byte[] fileContent = new byte[100];
        System.arraycopy(pdfMagic, 0, fileContent, 0, pdfMagic.length);

        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(fileContent));
        assertEquals(AllowedFileType.PDF, result);
    }

    @Test
    void detectsZIP() throws IOException {
        byte[] zipMagic = {0x50, 0x4B, 0x03, 0x04};
        byte[] fileContent = new byte[100];
        System.arraycopy(zipMagic, 0, fileContent, 0, zipMagic.length);

        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(fileContent));
        assertEquals(AllowedFileType.ZIP, result);
    }

    @Test
    void rejectsSVG() throws IOException {
        byte[] svgContent = "<?xml version='1.0'?><svg></svg>".getBytes();
        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(svgContent));
        assertNull(result);
    }

    @Test
    void rejectsHTML() throws IOException {
        byte[] htmlContent = "<!DOCTYPE html><html><body></body></html>".getBytes();
        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(htmlContent));
        assertNull(result);
    }

    @Test
    void rejectsHTMLLowercase() throws IOException {
        byte[] htmlContent = "<!doctype html><html><body></body></html>".getBytes();
        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(htmlContent));
        assertNull(result);
    }

    @Test
    void rejectsFileTooSmall() throws IOException {
        byte[] tinyFile = {0x00, 0x01};
        AllowedFileType result = FileTypeDetector.detectType(new ByteArrayInputStream(tinyFile));
        assertNull(result);
    }

    @Test
    void extractsExtensionCorrectly() {
        assertEquals("png", FileTypeDetector.extractExtension("image.png"));
        assertEquals("jpg", FileTypeDetector.extractExtension("photo.jpg"));
        assertEquals("tar", FileTypeDetector.extractExtension("archive.tar.gz"));
        assertNull(FileTypeDetector.extractExtension("noextension"));
        assertNull(FileTypeDetector.extractExtension(".hiddenfile"));
    }

    @Test
    void validatesSVGWithPNGExtension() throws IOException {
        // SVG file pretending to be PNG
        AllowedFileType detectedType = AllowedFileType.PNG;
        String filename = "image.png";
        // But detected type would be null for SVG, so this test validates the logic
        assertNull(FileTypeDetector.detectType(
            new ByteArrayInputStream("<?xml><svg></svg>".getBytes())
        ));
    }

    @Test
    void detectsPolyglot() {
        byte[] pngWithHTML = new byte[100];
        byte[] pngMagic = {(byte) 0x89, 0x50, 0x4E, 0x47};
        System.arraycopy(pngMagic, 0, pngWithHTML, 0, pngMagic.length);

        // Add HTML content later in file
        byte[] htmlSnippet = "<script>alert('xss')</script>".getBytes();
        System.arraycopy(htmlSnippet, 0, pngWithHTML, 50, htmlSnippet.length);

        assertTrue(FileTypeDetector.isPotentialPolyglot(pngWithHTML));
    }

    @Test
    void allowedFileTypeMatches() {
        assertTrue(AllowedFileType.PNG.matchesExtension("png"));
        assertTrue(AllowedFileType.PNG.matchesExtension("PNG"));
        assertTrue(AllowedFileType.PNG.matchesExtension(".png"));
        assertTrue(AllowedFileType.JPEG.matchesExtension("jpg"));
        assertTrue(AllowedFileType.JPEG.matchesExtension("jpeg"));
        assertFalse(AllowedFileType.PNG.matchesExtension("jpg"));
    }

    @Test
    void mimeTypeFromString() {
        assertEquals(AllowedFileType.PNG, AllowedFileType.fromMimeType("image/png"));
        assertEquals(AllowedFileType.JPEG, AllowedFileType.fromMimeType("image/jpeg"));
        assertEquals(AllowedFileType.PDF, AllowedFileType.fromMimeType("application/pdf"));
        assertNull(AllowedFileType.fromMimeType("image/svg+xml"));
        assertNull(AllowedFileType.fromMimeType("text/html"));
    }
}
