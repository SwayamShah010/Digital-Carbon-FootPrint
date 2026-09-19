package com.carbonauditor.analyzer;

import com.carbonauditor.model.FileRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 8: Unnecessary File Analyzer
 *
 * Flags files as old, large, or temporary and assigns a cleanup score
 * (higher score = stronger cleanup recommendation). Files are only ever
 * flagged for review -- nothing is deleted automatically.
 */
public class CleanupAnalyzer {

    // Configurable thresholds
    public static final int OLD_FILE_THRESHOLD_DAYS = 365;      // "not modified for > 1 year"
    public static final long LARGE_FILE_THRESHOLD_BYTES = 500L * 1024 * 1024; // 500 MB

    // Cleanup scoring weights (see spec section 10)
    private static final int SCORE_DUPLICATE = 40;
    private static final int SCORE_OLD = 25;
    private static final int SCORE_LARGE = 20;
    private static final int SCORE_TEMPORARY = 30;

    private final FileAnalyzer fileAnalyzer = new FileAnalyzer();

    public void analyze(List<FileRecord> files) {
        LocalDateTime now = LocalDateTime.now();

        for (FileRecord file : files) {
            boolean isOld = file.getLastModifiedDate() != null &&
                    file.getLastModifiedDate().isBefore(now.minusDays(OLD_FILE_THRESHOLD_DAYS));
            boolean isLarge = file.getFileSize() > LARGE_FILE_THRESHOLD_BYTES;
            boolean isTemp = fileAnalyzer.isTemporaryExtension(file.getExtension())
                    || "Temporary".equals(file.getCategory());

            file.setOld(isOld);
            file.setLarge(isLarge);
            file.setTemporary(isTemp);

            int score = 0;
            if (file.isDuplicate()) score += SCORE_DUPLICATE;
            if (isOld) score += SCORE_OLD;
            if (isLarge) score += SCORE_LARGE;
            if (isTemp) score += SCORE_TEMPORARY;
            file.setCleanupScore(score);
        }
    }

    /**
     * Returns files worth recommending for cleanup review, sorted by
     * cleanup score descending (highest score = strongest recommendation first).
     */
    public List<FileRecord> getRecommendedFiles(List<FileRecord> files, int minScore) {
        List<FileRecord> recommended = new ArrayList<>();
        for (FileRecord file : files) {
            if (file.getCleanupScore() >= minScore) {
                recommended.add(file);
            }
        }
        recommended.sort((a, b) -> Integer.compare(b.getCleanupScore(), a.getCleanupScore()));
        return recommended;
    }

    public List<FileRecord> getRecommendedFiles(List<FileRecord> files) {
        return getRecommendedFiles(files, 1);
    }

    public long totalOldFileStorage(List<FileRecord> files) {
        return files.stream().filter(FileRecord::isOld).mapToLong(FileRecord::getFileSize).sum();
    }

    public long totalLargeFileStorage(List<FileRecord> files) {
        return files.stream().filter(FileRecord::isLarge).mapToLong(FileRecord::getFileSize).sum();
    }

    public long totalTemporaryStorage(List<FileRecord> files) {
        return files.stream().filter(FileRecord::isTemporary).mapToLong(FileRecord::getFileSize).sum();
    }

    public long countLargeFiles(List<FileRecord> files) {
        return files.stream().filter(FileRecord::isLarge).count();
    }
}
