package com.adventacademy.contentservice.infrastructure.file;

import lombok.extern.slf4j.Slf4j;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
public class FileUploadTransaction {

    private final List<Path> uploadedFiles = new ArrayList<>();
    private final List<Path> createdDirectories = new ArrayList<>();
    private boolean committed = false;
    private boolean rolledBack = false;

    /**
     * Register a file as part of this transaction
     */
    public void registerFile(Path filePath) {
        if (committed || rolledBack) {
            throw new IllegalStateException("Transaction already finalized");
        }
        uploadedFiles.add(filePath);
        log.debug("Registered file in transaction: {}", filePath);
    }

    /**
     * Register a directory created as part of transaction
     */
    public void registerDirectory(Path dirPath) {
        if (committed || rolledBack) {
            throw new IllegalStateException("Transaction already finalized");
        }
        createdDirectories.add(dirPath);
    }

    /**
     * Commit transaction (all files stay on disk)
     */
    public synchronized void commit() {
        if (committed || rolledBack) {
            throw new IllegalStateException("Transaction already finalized");
        }
        committed = true;
        log.info("Transaction committed: {} files", uploadedFiles.size());
    }

    /**
     * Rollback transaction (delete all uploaded files and created directories)
     */
    public synchronized void rollback() {
        if (committed || rolledBack) {
            throw new IllegalStateException("Transaction already finalized");
        }
        rolledBack = true;

        // Delete files first (reverse order)
        for (int i = uploadedFiles.size() - 1; i >= 0; i--) {
            Path file = uploadedFiles.get(i);
            try {
                if (Files.exists(file)) {
                    Files.delete(file);
                    log.debug("Deleted file: {}", file);
                }
            } catch (Exception e) {
                log.error("Failed to delete file during rollback: {}", file, e);
            }
        }

        // Delete directories (reverse order)
        for (int i = createdDirectories.size() - 1; i >= 0; i--) {
            Path dir = createdDirectories.get(i);
            try {
                if (Files.exists(dir) && Files.list(dir).count() == 0) {
                    Files.delete(dir);
                    log.debug("Deleted directory: {}", dir);
                }
            } catch (Exception e) {
                log.debug("Failed to delete directory during rollback: {}", dir, e);
            }
        }

        log.info("Transaction rolled back: {} files deleted", uploadedFiles.size());
    }

    public boolean isCommitted() {
        return committed;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    public int getFileCount() {
        return uploadedFiles.size();
    }
}
