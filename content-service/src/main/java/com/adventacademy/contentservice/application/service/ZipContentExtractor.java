package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadAssetCommand;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.config.ArchiveUploadProperties;
import com.adventacademy.contentservice.domain.StoredAsset;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks a lesson-content zip bundle: exactly one Markdown file in the
 * archive root, plus optional bundled assets under {@code assets/}.
 * <p>
 * Each asset is saved through {@link UploadAssetService} (same validation as
 * a direct asset upload) and every {@code assets/<name>} reference in the
 * Markdown is rewritten to {@code asset:<uuid>}, so the result can be handed
 * straight to the regular content-upload pipeline, which already knows how
 * to resolve {@code asset:<uuid>} links.
 */
@Component
public class ZipContentExtractor {

    private static final String ASSETS_PREFIX = "assets/";

    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.ofEntries(
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".zip", "application/zip"),
            Map.entry(".mp4", "video/mp4")
    );

    private final UploadAssetService uploadAssetService;
    private final ArchiveUploadProperties properties;

    public ZipContentExtractor(UploadAssetService uploadAssetService, ArchiveUploadProperties properties) {
        this.uploadAssetService = uploadAssetService;
        this.properties = properties;
    }

    public String extract(byte[] zipBytes, UUID lessonId) {
        String rootMarkdown = null;
        Map<String, byte[]> assetBytesByPath = new LinkedHashMap<>();

        int entryCount = 0;
        long totalUncompressedBytes = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                entryCount++;
                if (entryCount > properties.getMaxEntryCount()) {
                    throw new InvalidMarkdownFileException(
                            "archive contains too many files, limit is " + properties.getMaxEntryCount());
                }

                String entryName = safeEntryName(entry.getName());
                long remainingBudget = properties.getMaxTotalUncompressedBytes() - totalUncompressedBytes;
                byte[] entryBytes = readEntryBounded(zis, remainingBudget);
                totalUncompressedBytes += entryBytes.length;

                if (isRootMarkdownFile(entryName)) {
                    if (rootMarkdown != null) {
                        throw new InvalidMarkdownFileException(
                                "archive must contain exactly one Markdown file in its root, found more than one");
                    }
                    rootMarkdown = new String(entryBytes, StandardCharsets.UTF_8);
                } else if (entryName.startsWith(ASSETS_PREFIX) && entryName.length() > ASSETS_PREFIX.length()) {
                    assetBytesByPath.put(entryName, entryBytes);
                }
                // any other entry (nested folders, files outside assets/, non-root .md) is ignored
            }
        } catch (IOException e) {
            throw new InvalidMarkdownFileException("cannot read zip archive", e);
        }

        if (rootMarkdown == null) {
            throw new InvalidMarkdownFileException("archive must contain a Markdown file in its root");
        }

        return rewriteAssetReferences(rootMarkdown, assetBytesByPath, lessonId);
    }

    private String rewriteAssetReferences(String markdown, Map<String, byte[]> assetBytesByPath, UUID lessonId) {
        String rewritten = markdown;
        for (Map.Entry<String, byte[]> asset : assetBytesByPath.entrySet()) {
            String path = asset.getKey();
            String filename = path.substring(ASSETS_PREFIX.length());
            String contentType = guessContentType(filename);

            StoredAsset stored = uploadAssetService.uploadAsset(
                    new UploadAssetCommand(lessonId, filename, contentType, asset.getValue()));

            rewritten = rewritten.replace(path, "asset:" + stored.getId());
        }
        return rewritten;
    }

    private static boolean isRootMarkdownFile(String entryName) {
        return !entryName.contains("/") && (entryName.endsWith(".md") || entryName.endsWith(".markdown"));
    }

    private static String safeEntryName(String rawName) {
        String normalized = rawName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new InvalidMarkdownFileException("archive contains an unsafe path: " + rawName);
        }
        return normalized;
    }

    private byte[] readEntryBounded(ZipInputStream zis, long remainingBudget) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long readSoFar = 0;
        int n;
        while ((n = zis.read(chunk)) != -1) {
            readSoFar += n;
            if (readSoFar > remainingBudget) {
                throw new InvalidMarkdownFileException(
                        "archive exceeds the total uncompressed size limit of "
                                + properties.getMaxTotalUncompressedBytes() + " bytes");
            }
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private static String guessContentType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        String extension = filename.substring(dot).toLowerCase();
        return EXTENSION_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}