package com.adventacademy.contentservice.application.validation;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Browser-executable formats are never accepted, even when the caller spoofs their MIME type. */
public final class AssetTypePolicy {
    private static final Map<String, Set<String>> EXTENSIONS = Map.ofEntries(
            Map.entry("image/png", Set.of("png")), Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
            Map.entry("image/jpg", Set.of("jpg", "jpeg")), Map.entry("image/gif", Set.of("gif")),
            Map.entry("image/webp", Set.of("webp")), Map.entry("application/pdf", Set.of("pdf")),
            Map.entry("application/zip", Set.of("zip")), Map.entry("application/x-zip-compressed", Set.of("zip")),
            Map.entry("text/plain", Set.of("txt")), Map.entry("video/mp4", Set.of("mp4")),
            Map.entry("video/mpeg", Set.of("mpeg", "mpg")), Map.entry("audio/mpeg", Set.of("mp3")),
            Map.entry("audio/mp3", Set.of("mp3")),
            Map.entry("application/msword", Set.of("doc")), Map.entry("application/vnd.ms-excel", Set.of("xls")),
            Map.entry("application/vnd.ms-powerpoint", Set.of("ppt")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx")));

    private AssetTypePolicy() { }

    public static boolean matches(String filename, String type, byte[] bytes) {
        if (filename == null || type == null || bytes == null || bytes.length == 0) { return false; }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.getOrDefault(type, Set.of()).contains(extension)) { return false; }
        return switch (type) {
            case "image/png" -> starts(bytes, 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10);
            case "image/jpeg", "image/jpg" -> starts(bytes, 0xff, 0xd8, 0xff);
            case "image/gif" -> ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a");
            case "image/webp" -> ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
            case "application/pdf" -> ascii(bytes, 0, "%PDF-");
            case "application/zip", "application/x-zip-compressed" -> zip(bytes);
            case "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint" ->
                    starts(bytes, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> zip(bytes);
            case "video/mp4" -> ascii(bytes, 4, "ftyp");
            case "video/mpeg" -> starts(bytes, 0, 0, 1, 0xba) || starts(bytes, 0, 0, 1, 0xb3);
            case "audio/mpeg", "audio/mp3" -> ascii(bytes, 0, "ID3")
                    || (bytes.length > 1 && (bytes[0] & 255) == 255 && (bytes[1] & 0xe0) == 0xe0);
            case "text/plain" -> validText(bytes);
            default -> false;
        };
    }

    public static boolean safeInline(String filename, String type, byte[] bytes) {
        return type != null && type.startsWith("image/") && matches(filename, type, bytes);
    }

    private static boolean zip(byte[] bytes) {
        return starts(bytes, 0x50, 0x4b, 3, 4) || starts(bytes, 0x50, 0x4b, 5, 6);
    }

    private static boolean validText(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            for (byte value : bytes) {
                if (value == 0) { return false; }
            }
            return true;
        } catch (java.nio.charset.CharacterCodingException exception) { return false; }
    }

    private static boolean starts(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) { return false; }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 255) != prefix[i]) { return false; }
        }
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        return bytes.length >= offset + expected.length()
                && new String(bytes, offset, expected.length(), StandardCharsets.US_ASCII).equals(expected);
    }
}
