package com.carbonauditor.analyzer;

import com.carbonauditor.model.FileRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupAnalyzerTest {

    private final CleanupAnalyzer analyzer = new CleanupAnalyzer();

    private FileRecord fileWith(long sizeBytes, LocalDateTime lastModified, String extension) {
        FileRecord f = new FileRecord("test." + extension, "/tmp/test." + extension, sizeBytes,
                extension, LocalDateTime.now(), lastModified, "Other");
        return f;
    }

    @Test
    void flagsOldFile_notModifiedInOverAYear() {
        FileRecord f = fileWith(1000, LocalDateTime.now().minusDays(400), "txt");
        analyzer.analyze(List.of(f));
        assertTrue(f.isOld());
    }

    @Test
    void doesNotFlagRecentFileAsOld() {
        FileRecord f = fileWith(1000, LocalDateTime.now().minusDays(10), "txt");
        analyzer.analyze(List.of(f));
        assertFalse(f.isOld());
    }

    @Test
    void flagsLargeFile_over500Mb() {
        FileRecord f = fileWith(600L * 1024 * 1024, LocalDateTime.now(), "mp4");
        analyzer.analyze(List.of(f));
        assertTrue(f.isLarge());
    }

    @Test
    void doesNotFlagSmallFileAsLarge() {
        FileRecord f = fileWith(10L * 1024 * 1024, LocalDateTime.now(), "mp4");
        analyzer.analyze(List.of(f));
        assertFalse(f.isLarge());
    }

    @Test
    void flagsTemporaryExtension() {
        FileRecord f = fileWith(1000, LocalDateTime.now(), "tmp");
        analyzer.analyze(List.of(f));
        assertTrue(f.isTemporary());
    }

    @Test
    void cleanupScore_accumulatesAcrossFlags() {
        // Old + large + temporary-extension file should score higher than a plain recent small file
        FileRecord flagged = fileWith(600L * 1024 * 1024, LocalDateTime.now().minusDays(400), "tmp");
        FileRecord clean = fileWith(1000, LocalDateTime.now(), "txt");

        List<FileRecord> files = new ArrayList<>(List.of(flagged, clean));
        analyzer.analyze(files);

        assertTrue(flagged.getCleanupScore() > clean.getCleanupScore());
        assertEquals(0, clean.getCleanupScore());
    }

    @Test
    void getRecommendedFiles_filtersAndSortsByScoreDescending() {
        FileRecord highScore = fileWith(600L * 1024 * 1024, LocalDateTime.now().minusDays(400), "tmp");
        FileRecord midScore = fileWith(1000, LocalDateTime.now().minusDays(400), "txt");
        FileRecord noScore = fileWith(1000, LocalDateTime.now(), "txt");

        List<FileRecord> files = new ArrayList<>(List.of(noScore, highScore, midScore));
        analyzer.analyze(files);

        List<FileRecord> recommended = analyzer.getRecommendedFiles(files, 1);

        assertEquals(2, recommended.size());
        assertTrue(recommended.get(0).getCleanupScore() >= recommended.get(1).getCleanupScore());
        assertFalse(recommended.contains(noScore));
    }

    @Test
    void totalOldFileStorage_sumsOnlyOldFiles() {
        FileRecord old1 = fileWith(1000, LocalDateTime.now().minusDays(400), "txt");
        FileRecord old2 = fileWith(2000, LocalDateTime.now().minusDays(500), "txt");
        FileRecord recent = fileWith(3000, LocalDateTime.now(), "txt");

        List<FileRecord> files = new ArrayList<>(List.of(old1, old2, recent));
        analyzer.analyze(files);

        assertEquals(3000, analyzer.totalOldFileStorage(files));
    }
}
