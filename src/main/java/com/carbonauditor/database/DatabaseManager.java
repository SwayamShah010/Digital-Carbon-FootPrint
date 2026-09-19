package com.carbonauditor.database;

import com.carbonauditor.model.FileRecord;
import com.carbonauditor.model.ScanResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 14: Database
 * Handles SQLite persistence for scan history using plain JDBC.
 * Tables: scans, files, duplicates, recommendations
 */
public class DatabaseManager {

    private static final String DB_URL_PREFIX = "jdbc:sqlite:";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final String dbUrl;

    public DatabaseManager(String dbFilePath) {
        this.dbUrl = DB_URL_PREFIX + dbFilePath;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /** Creates the required tables if they do not already exist. */
    public void initSchema() {
        String scans = """
                CREATE TABLE IF NOT EXISTS scans (
                    scan_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scan_date TEXT NOT NULL,
                    scanned_path TEXT,
                    total_storage INTEGER,
                    duplicate_storage INTEGER,
                    unused_storage INTEGER,
                    estimated_carbon REAL,
                    potential_savings REAL
                );
                """;

        String files = """
                CREATE TABLE IF NOT EXISTS files (
                    file_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scan_id INTEGER,
                    file_name TEXT,
                    file_path TEXT,
                    file_size INTEGER,
                    extension TEXT,
                    category TEXT,
                    last_modified TEXT,
                    is_duplicate INTEGER,
                    is_old INTEGER,
                    is_large INTEGER,
                    is_temporary INTEGER,
                    cleanup_score INTEGER,
                    FOREIGN KEY(scan_id) REFERENCES scans(scan_id)
                );
                """;

        String duplicates = """
                CREATE TABLE IF NOT EXISTS duplicates (
                    duplicate_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scan_id INTEGER,
                    group_hash TEXT,
                    file_id INTEGER,
                    FOREIGN KEY(scan_id) REFERENCES scans(scan_id),
                    FOREIGN KEY(file_id) REFERENCES files(file_id)
                );
                """;

        String recommendations = """
                CREATE TABLE IF NOT EXISTS recommendations (
                    recommendation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scan_id INTEGER,
                    message TEXT,
                    FOREIGN KEY(scan_id) REFERENCES scans(scan_id)
                );
                """;

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(scans);
            stmt.execute(files);
            stmt.execute(duplicates);
            stmt.execute(recommendations);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    /**
     * Persists a scan result (summary + files + recommendations) and returns
     * the generated scan_id.
     */
    public long saveScan(ScanResult result, List<String> recommendations) {
        String insertScan = """
                INSERT INTO scans (scan_date, scanned_path, total_storage, duplicate_storage,
                                    unused_storage, estimated_carbon, potential_savings)
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            long scanId;

            try (PreparedStatement ps = conn.prepareStatement(insertScan, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, result.getScanDate().format(FMT));
                ps.setString(2, result.getScannedPath());
                ps.setLong(3, result.getTotalStorageBytes());
                ps.setLong(4, result.getDuplicateStorageBytes());
                ps.setLong(5, result.getOldFileStorageBytes() + result.getTemporaryStorageBytes());
                ps.setDouble(6, result.getEstimatedCarbonKg());
                ps.setDouble(7, result.getPotentialCarbonSavingsKg());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    scanId = keys.getLong(1);
                }
            }

            String insertFile = """
                    INSERT INTO files (scan_id, file_name, file_path, file_size, extension, category,
                                        last_modified, is_duplicate, is_old, is_large, is_temporary, cleanup_score)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertFile)) {
                for (FileRecord f : result.getRecommendedForCleanup()) {
                    ps.setLong(1, scanId);
                    ps.setString(2, f.getFileName());
                    ps.setString(3, f.getFilePath());
                    ps.setLong(4, f.getFileSize());
                    ps.setString(5, f.getExtension());
                    ps.setString(6, f.getCategory());
                    ps.setString(7, f.getLastModifiedDate() != null ? f.getLastModifiedDate().format(FMT) : null);
                    ps.setInt(8, f.isDuplicate() ? 1 : 0);
                    ps.setInt(9, f.isOld() ? 1 : 0);
                    ps.setInt(10, f.isLarge() ? 1 : 0);
                    ps.setInt(11, f.isTemporary() ? 1 : 0);
                    ps.setInt(12, f.getCleanupScore());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            String insertRec = "INSERT INTO recommendations (scan_id, message) VALUES (?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(insertRec)) {
                for (String message : recommendations) {
                    ps.setLong(1, scanId);
                    ps.setString(2, message);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return scanId;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save scan to database", e);
        }
    }

    /** Returns a summary list of past scans, most recent first. */
    public List<ScanSummary> getScanHistory() {
        String query = "SELECT scan_id, scan_date, scanned_path, total_storage, duplicate_storage, " +
                "unused_storage, estimated_carbon, potential_savings FROM scans ORDER BY scan_date DESC;";
        List<ScanSummary> history = new ArrayList<>();

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                history.add(new ScanSummary(
                        rs.getLong("scan_id"),
                        rs.getString("scan_date"),
                        rs.getString("scanned_path"),
                        rs.getLong("total_storage"),
                        rs.getLong("duplicate_storage"),
                        rs.getLong("unused_storage"),
                        rs.getDouble("estimated_carbon"),
                        rs.getDouble("potential_savings")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load scan history", e);
        }

        return history;
    }

    /** Lightweight DTO representing a row of the scans table, for history display. */
    public record ScanSummary(
            long scanId,
            String scanDate,
            String scannedPath,
            long totalStorage,
            long duplicateStorage,
            long unusedStorage,
            double estimatedCarbonKg,
            double potentialSavingsKg
    ) {
    }
}
