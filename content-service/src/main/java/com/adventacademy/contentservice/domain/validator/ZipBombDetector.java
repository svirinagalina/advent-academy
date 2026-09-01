package com.adventacademy.contentservice.domain.validator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZipBombDetector {

    // Configuration limits
    private static final long MAX_UNCOMPRESSED_SIZE = 1_000_000_000L; // 1GB
    private static final long MAX_COMPRESSED_ENTRY_SIZE = 100_000_000L; // 100MB
    private static final long MAX_COMPRESSION_RATIO = 100; // 100:1
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final long MAX_TOTAL_UNCOMPRESSED = 5_000_000_000L; // 5GB (cumulative)

    private long totalUncompressedSize = 0;
    private int entryCount = 0;

    /**
     * Check if ZIP entry represents a potential bomb
     */
    public void validateEntry(long compressedSize, long uncompressedSize, String entryName) {
        // Validate individual entry
        if (uncompressedSize > MAX_UNCOMPRESSED_SIZE) {
            log.warn("Entry uncompressed size too large: {} (max: {})", uncompressedSize, MAX_UNCOMPRESSED_SIZE);
            throw new IllegalArgumentException("ZIP entry exceeds maximum uncompressed size");
        }

        if (compressedSize > MAX_COMPRESSED_ENTRY_SIZE) {
            log.warn("Entry compressed size too large: {} (max: {})", compressedSize, MAX_COMPRESSED_ENTRY_SIZE);
            throw new IllegalArgumentException("ZIP entry exceeds maximum compressed size");
        }

        // Check compression ratio (potential bomb indicator)
        if (compressedSize > 0) {
            long ratio = uncompressedSize / compressedSize;
            if (ratio > MAX_COMPRESSION_RATIO) {
                log.warn("Suspicious compression ratio: {}:1 for entry {}", ratio, entryName);
                throw new IllegalArgumentException("ZIP entry has suspicious compression ratio (potential bomb)");
            }
        }

        // Track cumulative sizes
        totalUncompressedSize += uncompressedSize;
        if (totalUncompressedSize > MAX_TOTAL_UNCOMPRESSED) {
            log.warn("Total uncompressed size exceeds limit: {} (max: {})", totalUncompressedSize, MAX_TOTAL_UNCOMPRESSED);
            throw new IllegalArgumentException("ZIP exceeds maximum total uncompressed size");
        }

        entryCount++;
        if (entryCount > MAX_ZIP_ENTRIES) {
            log.warn("ZIP contains too many entries: {} (max: {})", entryCount, MAX_ZIP_ENTRIES);
            throw new IllegalArgumentException("ZIP contains too many entries");
        }
    }

    public long getTotalUncompressedSize() {
        return totalUncompressedSize;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void reset() {
        totalUncompressedSize = 0;
        entryCount = 0;
    }
}
