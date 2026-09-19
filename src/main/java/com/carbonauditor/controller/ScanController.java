package com.carbonauditor.controller;

import com.carbonauditor.analyzer.CleanupAnalyzer;
import com.carbonauditor.analyzer.DuplicateDetector;
import com.carbonauditor.analyzer.FileAnalyzer;
import com.carbonauditor.carbon.CarbonCalculator;
import com.carbonauditor.cloud.CloudFileScanner;
import com.carbonauditor.cloud.GoogleDriveAuth;
import com.carbonauditor.database.DatabaseManager;
import com.carbonauditor.model.FileRecord;
import com.carbonauditor.model.Recommendation;
import com.carbonauditor.model.ScanResult;
import com.carbonauditor.recommendation.RecommendationEngine;
import com.carbonauditor.scanner.FileScanner;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orchestrates the full scan pipeline for both local storage and cloud
 * storage sources:
 * Scan -> Classify -> Detect duplicates -> Flag old/large/temp -> Estimate
 * carbon -> Generate recommendations -> Persist to database.
 *
 * This class is UI-agnostic; DashboardController calls into it and updates
 * JavaFX nodes with the resulting ScanResult.
 */
public class ScanController {

    private final FileScanner fileScanner = new FileScanner();
    private final CloudFileScanner cloudFileScanner = new CloudFileScanner();
    private final FileAnalyzer fileAnalyzer = new FileAnalyzer();
    private final DuplicateDetector duplicateDetector = new DuplicateDetector();
    private final CleanupAnalyzer cleanupAnalyzer = new CleanupAnalyzer();
    private final CarbonCalculator carbonCalculator = new CarbonCalculator();
    private final RecommendationEngine recommendationEngine = new RecommendationEngine();
    private final DatabaseManager databaseManager;

    public ScanController(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Runs a full audit of the given local path and returns the aggregated
     * result. Also persists the scan to the database. If isCancelled
     * becomes true mid-scan, throws ScanCancelledException and nothing is
     * saved to the database.
     */
    public ScanResult runFullScan(String path, Consumer<Integer> progressCallback,
                                   java.util.function.BooleanSupplier isCancelled) throws IOException {
        List<FileRecord> files = fileScanner.scan(path, progressCallback, isCancelled);
        List<List<FileRecord>> duplicateGroups = duplicateDetector.findDuplicates(files);
        return buildResult(path, files, duplicateGroups);
    }

    public ScanResult runFullScan(String path, Consumer<Integer> progressCallback) throws IOException {
        return runFullScan(path, progressCallback, null);
    }

    public ScanResult runFullScan(String path) throws IOException {
        return runFullScan(path, null, null);
    }

    /**
     * Runs a full audit of the signed-in user's Google Drive and returns
     * the aggregated result, using the same pipeline as a local scan.
     * Duplicate detection uses Drive's own md5Checksum field rather than
     * downloading file contents. Requires credentials.json to be present
     * (see README.md "Google Drive Setup") — the first call will open the
     * user's browser for a one-time consent flow. If isCancelled becomes
     * true mid-scan, throws ScanCancelledException and nothing is saved.
     */
    public ScanResult runCloudScan(Consumer<Integer> progressCallback,
                                    java.util.function.BooleanSupplier isCancelled) throws Exception {
        List<FileRecord> files = cloudFileScanner.scan(progressCallback, isCancelled);
        List<List<FileRecord>> duplicateGroups = duplicateDetector.findDuplicatesUsingProvidedHashes(files);
        return buildResult("Google Drive", files, duplicateGroups);
    }

    public ScanResult runCloudScan(Consumer<Integer> progressCallback) throws Exception {
        return runCloudScan(progressCallback, null);
    }

    /** Shared post-processing pipeline used by both local and cloud scans. */
    private ScanResult buildResult(String scannedPathLabel, List<FileRecord> files,
                                    List<List<FileRecord>> duplicateGroups) {
        // Flag old / large / temporary files and assign cleanup scores
        cleanupAnalyzer.analyze(files);

        ScanResult result = new ScanResult();
        result.setScannedPath(scannedPathLabel);
        result.setAllFiles(files);
        result.setDuplicateGroups(duplicateGroups);

        long totalBytes = fileAnalyzer.totalStorage(files);
        // Only count "extra" duplicate copies (keep one) toward reclaimable storage
        long duplicateReclaimable = duplicateDetector.totalDuplicateStorage(duplicateGroups);

        long oldBytes = cleanupAnalyzer.totalOldFileStorage(files);
        long largeBytes = cleanupAnalyzer.totalLargeFileStorage(files);
        long tempBytes = cleanupAnalyzer.totalTemporaryStorage(files);

        List<FileRecord> recommended = cleanupAnalyzer.getRecommendedFiles(files, 1);

        result.setTotalStorageBytes(totalBytes);
        result.setDuplicateStorageBytes(duplicateReclaimable);
        result.setOldFileStorageBytes(oldBytes);
        result.setLargeFileStorageBytes(largeBytes);
        result.setTemporaryStorageBytes(tempBytes);
        result.setRecommendedForCleanup(recommended);
        result.setStorageByCategory(fileAnalyzer.aggregateByCategory(files));

        long potentialCleanupBytes = recommended.stream().mapToLong(FileRecord::getFileSize).sum();
        result.setPotentialCleanupBytes(potentialCleanupBytes);

        double totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);
        double cleanupGb = potentialCleanupBytes / (1024.0 * 1024.0 * 1024.0);
        result.setEstimatedCarbonKg(carbonCalculator.estimateCarbonKg(totalGb));
        result.setPotentialCarbonSavingsKg(carbonCalculator.estimatePotentialSavingsKg(cleanupGb));

        List<Recommendation> recommendations = recommendationEngine.generateRecommendations(result);

        if (databaseManager != null) {
            databaseManager.saveScan(result, recommendationEngine.toPlainText(recommendations));
        }

        return result;
    }

    public CarbonCalculator getCarbonCalculator() {
        return carbonCalculator;
    }

    public GoogleDriveAuth getGoogleDriveAuth() {
        return cloudFileScanner.getAuth();
    }
}
