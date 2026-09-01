package com.adventacademy.contentservice.adapter.rest.builder;

import com.adventacademy.contentservice.domain.model.AllowedFileType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class AssetResponseBuilderTest {

    private final AssetResponseBuilder builder = new AssetResponseBuilder();

    @Test
    void servesPNGInline() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.PNG,
                "image.png"
        );

        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst("X-Content-Type-Options").contains("nosniff"));
        assertTrue(response.getHeaders().getContentDisposition().isInline());
    }

    @Test
    void servesJPEGInline() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.JPEG,
                "photo.jpg"
        );

        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentDisposition().isInline());
    }

    @Test
    void servesGIFInline() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.GIF,
                "animation.gif"
        );

        assertEquals(MediaType.IMAGE_GIF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentDisposition().isInline());
    }

    @Test
    void servesPDFInline() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.PDF,
                "document.pdf"
        );

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentDisposition().isInline());
    }

    @Test
    void servesZIPAsAttachment() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.ZIP,
                "archive.zip"
        );

        assertEquals(MediaType.APPLICATION_ZIP, response.getHeaders().getContentType());
        assertFalse(response.getHeaders().getContentDisposition().isInline());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("attachment"));
    }

    @Test
    void includesNosniffHeader() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.PNG,
                "image.png"
        );

        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void setCacheControlForImages() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.PNG,
                "image.png"
        );

        assertTrue(response.getHeaders().getCacheControl().contains("max-age=3600"));
    }

    @Test
    void setCacheControlForPDF() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.PDF,
                "document.pdf"
        );

        assertTrue(response.getHeaders().getCacheControl().contains("max-age=3600"));
    }

    @Test
    void noCacheForZIP() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.ZIP,
                "archive.zip"
        );

        assertNull(response.getHeaders().getCacheControl());
    }

    @Test
    void includesFrameOptions() {
        ResponseEntity<byte[]> response = builder.buildAssetResponse(
                new byte[]{1, 2, 3},
                AllowedFileType.PNG,
                "image.png"
        );

        assertEquals("DENY", response.getHeaders().getFirst("X-Frame-Options"));
    }
}
