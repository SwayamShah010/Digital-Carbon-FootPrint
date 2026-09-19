package com.carbonauditor.database;

import com.carbonauditor.model.FileRecord;
import com.carbonauditor.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    private ScanResult sampleResult(String path, long totalBytes, double carbonKg) {
        ScanResult result = new ScanResult();
        result.setScannedPath(path);
        result.setTotalStorageBytes(totalBytes);
        result.setEstimatedCarbonKg(carbonKg);
        result.setPotentialCarbonSavingsKg(carbonKg * 0.1);
        result.setRecommendedForCleanup(List.of());
        return result;
    }

    @Test
    void saveScan_thenHistory_containsTheSavedScan(@TempDir Path tempDir) {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("test.db").toString());
        db.initSchema();

        ScanResult result = sampleResult("/home/user/Documents", 5_000_000_000L, 12.5);
        db.saveScan(result, List.of("Test recommendation"));

        List<DatabaseManager.ScanSummary> history = db.getScanHistory();

        assertEquals(1, history.size());
        assertEquals("/home/user/Documents", history.get(0).scannedPath());
        assertEquals(5_000_000_000L, history.get(0).totalStorage());
        assertEquals(12.5, history.get(0).estimatedCarbonKg(), 0.001);
    }

    @Test
    void scanHistory_isOrderedNewestFirst(@TempDir Path tempDir) throws InterruptedException {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("test.db").toString());
        db.initSchema();

        ScanResult first = sampleResult("/data", 1_000_000_000L, 1.0);
        db.saveScan(first, List.of());
        Thread.sleep(5); // ensure distinct timestamps
        ScanResult second = sampleResult("/data", 2_000_000_000L, 2.0);
        db.saveScan(second, List.of());

        List<DatabaseManager.ScanSummary> history = db.getScanHistory();

        assertEquals(2, history.size());
        assertTrue(history.get(0).scanDate().compareTo(history.get(1).scanDate()) >= 0,
                "Most recent scan should be first");
    }

    @Test
    void emptyDatabase_returnsEmptyHistory(@TempDir Path tempDir) {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("empty.db").toString());
        db.initSchema();
        assertTrue(db.getScanHistory().isEmpty());
    }
}
