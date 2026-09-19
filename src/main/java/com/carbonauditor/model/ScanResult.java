package com.carbonauditor.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated results of a single storage scan.
 */
public class ScanResult {

    private LocalDateTime scanDate = LocalDateTime.now();
    private String scannedPath;

    private List<FileRecord> allFiles = new ArrayList<>();
    private List<List<FileRecord>> duplicateGroups = new ArrayList<>();
    private List<FileRecord> recommendedForCleanup = new ArrayList<>();

    private long totalStorageBytes;
    private long duplicateStorageBytes;
    private long oldFileStorageBytes;
    private long largeFileStorageBytes;
    private long temporaryStorageBytes;
    private long potentialCleanupBytes;

    private double estimatedCarbonKg;
    private double potentialCarbonSavingsKg;

    private Map<String, Long> storageByCategory = new LinkedHashMap<>();

    public LocalDateTime getScanDate() {
        return scanDate;
    }

    public void setScanDate(LocalDateTime scanDate) {
        this.scanDate = scanDate;
    }

    public String getScannedPath() {
        return scannedPath;
    }

    public void setScannedPath(String scannedPath) {
        this.scannedPath = scannedPath;
    }

    public List<FileRecord> getAllFiles() {
        return allFiles;
    }

    public void setAllFiles(List<FileRecord> allFiles) {
        this.allFiles = allFiles;
    }

    public List<List<FileRecord>> getDuplicateGroups() {
        return duplicateGroups;
    }

    public void setDuplicateGroups(List<List<FileRecord>> duplicateGroups) {
        this.duplicateGroups = duplicateGroups;
    }

    public List<FileRecord> getRecommendedForCleanup() {
        return recommendedForCleanup;
    }

    public void setRecommendedForCleanup(List<FileRecord> recommendedForCleanup) {
        this.recommendedForCleanup = recommendedForCleanup;
    }

    public long getTotalStorageBytes() {
        return totalStorageBytes;
    }

    public void setTotalStorageBytes(long totalStorageBytes) {
        this.totalStorageBytes = totalStorageBytes;
    }

    public long getDuplicateStorageBytes() {
        return duplicateStorageBytes;
    }

    public void setDuplicateStorageBytes(long duplicateStorageBytes) {
        this.duplicateStorageBytes = duplicateStorageBytes;
    }

    public long getOldFileStorageBytes() {
        return oldFileStorageBytes;
    }

    public void setOldFileStorageBytes(long oldFileStorageBytes) {
        this.oldFileStorageBytes = oldFileStorageBytes;
    }

    public long getLargeFileStorageBytes() {
        return largeFileStorageBytes;
    }

    public void setLargeFileStorageBytes(long largeFileStorageBytes) {
        this.largeFileStorageBytes = largeFileStorageBytes;
    }

    public long getTemporaryStorageBytes() {
        return temporaryStorageBytes;
    }

    public void setTemporaryStorageBytes(long temporaryStorageBytes) {
        this.temporaryStorageBytes = temporaryStorageBytes;
    }

    public long getPotentialCleanupBytes() {
        return potentialCleanupBytes;
    }

    public void setPotentialCleanupBytes(long potentialCleanupBytes) {
        this.potentialCleanupBytes = potentialCleanupBytes;
    }

    public double getEstimatedCarbonKg() {
        return estimatedCarbonKg;
    }

    public void setEstimatedCarbonKg(double estimatedCarbonKg) {
        this.estimatedCarbonKg = estimatedCarbonKg;
    }

    public double getPotentialCarbonSavingsKg() {
        return potentialCarbonSavingsKg;
    }

    public void setPotentialCarbonSavingsKg(double potentialCarbonSavingsKg) {
        this.potentialCarbonSavingsKg = potentialCarbonSavingsKg;
    }

    public Map<String, Long> getStorageByCategory() {
        return storageByCategory;
    }

    public void setStorageByCategory(Map<String, Long> storageByCategory) {
        this.storageByCategory = storageByCategory;
    }

    public double getTotalStorageGB() {
        return totalStorageBytes / (1024.0 * 1024.0 * 1024.0);
    }

    public double getPotentialCleanupGB() {
        return potentialCleanupBytes / (1024.0 * 1024.0 * 1024.0);
    }

    public double getPotentialStorageReductionPercent() {
        if (totalStorageBytes == 0) return 0.0;
        return (potentialCleanupBytes * 100.0) / totalStorageBytes;
    }
}
