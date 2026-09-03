package com.adventacademy.contentservice.domain.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class ZipBombDetectorTest {

    private ZipBombDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ZipBombDetector();
    }

    @Test
    void acceptsNormalFile() {
        assertDoesNotThrow(() ->
            detector.validateEntry(1000, 2000, "file.txt")
        );
    }

    @Test
    void rejectsExcessiveUncompressedSize() {
        assertThrows(IllegalArgumentException.class, () ->
            detector.validateEntry(100, 2_000_000_000L, "bomb.txt")
        );
    }

    @Test
    void rejectsExcessiveCompressedSize() {
        assertThrows(IllegalArgumentException.class, () ->
            detector.validateEntry(200_000_000L, 1000, "bomb.zip")
        );
    }

    @Test
    void rejectsSuspiciousCompressionRatio() {
        // 1 byte compressed to 200 MB (200M:1 ratio)
        assertThrows(IllegalArgumentException.class, () ->
            detector.validateEntry(1, 200_000_000L, "bomb.txt")
        );
    }

    @Test
    void acceptsNormalCompressionRatio() {
        // 10:1 compression ratio is normal and acceptable
        assertDoesNotThrow(() ->
            detector.validateEntry(1000, 10_000, "file.txt")
        );
    }

    @Test
    void rejectsTooManyEntries() {
        for (int i = 0; i < 10_000; i++) {
            final int index = i;
            assertDoesNotThrow(() ->
                detector.validateEntry(100, 200, "file" + index + ".txt")
            );
        }

        // 10,001st entry should be rejected
        assertThrows(IllegalArgumentException.class, () ->
            detector.validateEntry(100, 200, "file10001.txt")
        );
    }

    @Test
    void rejectsTotalUncompressedSizeExceeded() {
        // Add entries until total exceeds limit
        for (int i = 0; i < 50; i++) {
            final int index = i;
            assertDoesNotThrow(() ->
                detector.validateEntry(10_000_000, 100_000_000, "file" + index + ".bin")
            );
        }

        // Next entry should exceed 5GB total
        assertThrows(IllegalArgumentException.class, () ->
            detector.validateEntry(10_000_000, 1_000_000_000, "bomb.bin")
        );
    }

    @Test
    void tracksTotalSize() {
        detector.validateEntry(100, 1000, "file1.txt");
        detector.validateEntry(200, 2000, "file2.txt");

        assertEquals(3000, detector.getTotalUncompressedSize());
        assertEquals(2, detector.getEntryCount());
    }

    @Test
    void resetClearsState() {
        detector.validateEntry(100, 1000, "file.txt");
        assertEquals(1, detector.getEntryCount());

        detector.reset();
        assertEquals(0, detector.getEntryCount());
        assertEquals(0, detector.getTotalUncompressedSize());
    }
}
