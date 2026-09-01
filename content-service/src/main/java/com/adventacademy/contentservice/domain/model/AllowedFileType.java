package com.adventacademy.contentservice.domain.model;

public enum AllowedFileType {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg", "jpeg"),
    GIF("image/gif", "gif"),
    PDF("application/pdf", "pdf"),
    ZIP("application/zip", "zip");

    private final String mimeType;
    private final String[] extensions;

    AllowedFileType(String mimeType, String... extensions) {
        this.mimeType = mimeType;
        this.extensions = extensions;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String[] getExtensions() {
        return extensions;
    }

    public boolean matchesExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        String normalized = extension.toLowerCase().replaceFirst("^\\.", "");
        for (String ext : extensions) {
            if (ext.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static AllowedFileType fromMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String normalized = mimeType.toLowerCase().split(";")[0].trim();
        for (AllowedFileType type : values()) {
            if (type.mimeType.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }
}
