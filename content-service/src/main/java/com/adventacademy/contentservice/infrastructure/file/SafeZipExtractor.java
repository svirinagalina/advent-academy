package com.adventacademy.contentservice.infrastructure.file;

import com.adventacademy.contentservice.domain.validator.ZipBombDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class SafeZipExtractor {

    private final Path tempDirectory;
    private final long extractionTimeoutMillis;

    public SafeZipExtractor(
            @Value("${upload.temp-directory:/tmp/advent-uploads}") String tempDir,
            @Value("${upload.extraction-timeout-seconds:30}") long timeoutSeconds) {
        this.tempDirectory = Paths.get(tempDir);
        this.extractionTimeoutMillis = timeoutSeconds * 1000;
    }

    /**
     * Safely extract ZIP file with all protections:
     * - ZIP slip (path traversal) prevention
     * - ZIP bomb detection
     * - Nested archive prevention
     * - Duplicate entry detection
     * - Malformed file handling
     * - Atomic extraction (all-or-nothing)
     */
    public Path extractZipFile(InputStream zipInputStream, String archiveName) throws IOException {
        Path stagingDirectory = createStagingDirectory(archiveName);

        try {
            performExtraction(zipInputStream, stagingDirectory);
            log.info("ZIP file extracted successfully: {}", archiveName);
            return stagingDirectory;
        } catch (Exception e) {
            log.error("ZIP extraction failed, rolling back: {}", archiveName, e);
            rollbackExtraction(stagingDirectory);
            throw new IOException("Failed to extract ZIP file: " + e.getMessage(), e);
        }
    }

    private void performExtraction(InputStream zipInputStream, Path stagingDirectory) throws IOException {
        ZipBombDetector bombDetector = new ZipBombDetector();
        Set<String> seenEntries = new HashSet<>();
        long extractionStart = System.currentTimeMillis();

        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Check timeout
                if (System.currentTimeMillis() - extractionStart > extractionTimeoutMillis) {
                    throw new IOException("ZIP extraction timeout exceeded");
                }

                // Skip directory entries
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                log.debug("Processing ZIP entry: {}", entryName);

                // Validate entry name (path traversal prevention)
                validateZipSlipPrevention(entryName);

                // Check for duplicate entries
                if (seenEntries.contains(entryName)) {
                    log.warn("Duplicate entry found in ZIP: {}", entryName);
                    throw new IOException("ZIP contains duplicate entries");
                }
                seenEntries.add(entryName);

                // Check for nested archives
                validateNoNestedArchives(entryName);

                // Validate bomb indicators
                bombDetector.validateEntry(
                        entry.getCompressedSize(),
                        entry.getSize(),
                        entryName
                );

                // Extract entry
                extractEntry(zis, stagingDirectory, entryName);
            }
        }

        log.info("ZIP extraction complete: {} entries, {} MB uncompressed",
                bombDetector.getEntryCount(),
                bombDetector.getTotalUncompressedSize() / 1024 / 1024);
    }

    /**
     * ZIP Slip Prevention:
     * Ensure entry resolves within target directory
     */
    private void validateZipSlipPrevention(String entryName) throws IOException {
        if (entryName.contains("..")) {
            log.warn("ZIP slip attempt detected: {}", entryName);
            throw new IOException("ZIP entry contains path traversal (ZIP slip)");
        }

        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            log.warn("Absolute path in ZIP entry: {}", entryName);
            throw new IOException("ZIP entry uses absolute path");
        }

        // Additional check: resolve and verify it's still within target
        Path resolved = Paths.get(entryName).normalize();
        if (resolved.isAbsolute() || resolved.toString().startsWith("..")) {
            log.warn("ZIP entry resolves outside target directory: {}", entryName);
            throw new IOException("ZIP entry resolves outside extraction directory");
        }
    }

    /**
     * Prevent nested archives (ZIP, TAR, GZ inside ZIP)
     */
    private void validateNoNestedArchives(String entryName) throws IOException {
        String lowerName = entryName.toLowerCase();
        if (lowerName.endsWith(".zip") ||
            lowerName.endsWith(".tar") ||
            lowerName.endsWith(".gz") ||
            lowerName.endsWith(".tar.gz") ||
            lowerName.endsWith(".rar") ||
            lowerName.endsWith(".7z")) {
            log.warn("Nested archive detected in ZIP: {}", entryName);
            throw new IOException("ZIP files cannot contain nested archives");
        }
    }

    private void extractEntry(ZipInputStream zis, Path stagingDirectory, String entryName) throws IOException {
        Path entryPath = stagingDirectory.resolve(entryName).normalize();

        // Verify normalized path is still within staging directory
        if (!entryPath.normalize().toString().startsWith(stagingDirectory.normalize().toString())) {
            log.warn("ZIP slip detected during extraction: {}", entryName);
            throw new IOException("Entry path resolves outside staging directory");
        }

        // Create parent directories
        Files.createDirectories(entryPath.getParent());

        // Write entry file
        try (OutputStream os = new FileOutputStream(entryPath.toFile())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = zis.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
    }

    private Path createStagingDirectory(String archiveName) throws IOException {
        String safeName = archiveName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String timestamp = String.valueOf(System.currentTimeMillis());
        Path stagingDir = tempDirectory.resolve(safeName + "_" + timestamp);

        Files.createDirectories(stagingDir);
        log.debug("Created staging directory: {}", stagingDir);
        return stagingDir;
    }

    /**
     * Rollback: Delete entire staging directory on error
     * Ensures no partial files remain
     */
    private void rollbackExtraction(Path stagingDirectory) {
        if (stagingDirectory == null || !Files.exists(stagingDirectory)) {
            return;
        }

        try {
            Files.walk(stagingDirectory)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("Deleted: {}", path);
                        } catch (IOException e) {
                            log.error("Failed to delete: {}", path, e);
                        }
                    });
            log.info("Rollback complete: {}", stagingDirectory);
        } catch (IOException e) {
            log.error("Rollback failed for {}", stagingDirectory, e);
        }
    }
}
