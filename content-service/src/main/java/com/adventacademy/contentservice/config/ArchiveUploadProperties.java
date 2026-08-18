package com.adventacademy.contentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content.upload.archive")
public class ArchiveUploadProperties {

    private long maxTotalUncompressedBytes;
    private int maxEntryCount;

    public long getMaxTotalUncompressedBytes() {
        return maxTotalUncompressedBytes;
    }

    public void setMaxTotalUncompressedBytes(long maxTotalUncompressedBytes) {
        this.maxTotalUncompressedBytes = maxTotalUncompressedBytes;
    }

    public int getMaxEntryCount() {
        return maxEntryCount;
    }

    public void setMaxEntryCount(int maxEntryCount) {
        this.maxEntryCount = maxEntryCount;
    }
}