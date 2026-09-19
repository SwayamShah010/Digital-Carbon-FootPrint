package com.carbonauditor.model;

import java.time.LocalDateTime;

/**
 * Represents metadata about a single scanned file.
 */
public class FileRecord {

    private String fileName;
    private String filePath;
    private long fileSize; // bytes
    private String extension;
    private LocalDateTime creationDate;
    private LocalDateTime lastModifiedDate;
    private String category; // Documents, Images, Videos, Audio, Archives, Applications, Temporary, Other

    private String sha256Hash; // populated lazily by DuplicateDetector, or supplied directly by a cloud source
    private boolean duplicate;
    private boolean old;
    private boolean large;
    private boolean temporary;
    private int cleanupScore;

    /** Where this file was found: "Local" or "Google Drive". Defaults to "Local". */
    private String source = "Local";

    /** For cloud files, the provider's own file ID (used for building open/view links). Null for local files. */
    private String cloudFileId;

    public FileRecord() {
    }

    public FileRecord(String fileName, String filePath, long fileSize, String extension,
                       LocalDateTime creationDate, LocalDateTime lastModifiedDate, String category) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.extension = extension;
        this.creationDate = creationDate;
        this.lastModifiedDate = lastModifiedDate;
        this.category = category;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public boolean isOld() {
        return old;
    }

    public void setOld(boolean old) {
        this.old = old;
    }

    public boolean isLarge() {
        return large;
    }

    public void setLarge(boolean large) {
        this.large = large;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
    }

    public int getCleanupScore() {
        return cleanupScore;
    }

    public void setCleanupScore(int cleanupScore) {
        this.cleanupScore = cleanupScore;
    }

    public double getFileSizeMB() {
        return fileSize / (1024.0 * 1024.0);
    }

    public double getFileSizeGB() {
        return fileSize / (1024.0 * 1024.0 * 1024.0);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCloudFileId() {
        return cloudFileId;
    }

    public void setCloudFileId(String cloudFileId) {
        this.cloudFileId = cloudFileId;
    }

    @Override
    public String toString() {
        return fileName + " (" + filePath + ") - " + fileSize + " bytes";
    }
}
