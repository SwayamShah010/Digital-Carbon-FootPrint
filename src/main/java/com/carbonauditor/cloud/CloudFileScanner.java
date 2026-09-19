package com.carbonauditor.cloud;

import com.carbonauditor.analyzer.FileAnalyzer;
import com.carbonauditor.model.FileRecord;
import com.carbonauditor.scanner.ScanCancelledException;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Module 15: Cloud Storage Scanner — Google Drive.
 *
 * Lists the signed-in user's Drive files (read-only, metadata only — file
 * contents are never downloaded) and converts them into the same
 * {@link FileRecord} model used for local scans, so the rest of the
 * pipeline (classification, duplicate detection, cleanup scoring, carbon
 * estimation, recommendations) works identically regardless of source.
 *
 * Duplicate detection for Drive files uses Drive's own md5Checksum field
 * (returned directly by the API) instead of downloading and hashing file
 * contents locally — see {@code DuplicateDetector.findDuplicatesUsingProvidedHashes}.
 * Google-native files (Docs, Sheets, Slides) have no fixed byte content or
 * checksum, so they're included for storage/category totals but excluded
 * from duplicate detection.
 */
public class CloudFileScanner {

    private static final String FIELDS = "nextPageToken, files(id, name, size, mimeType, "
            + "createdTime, modifiedTime, md5Checksum, trashed)";
    private static final int PAGE_SIZE = 1000;

    private final FileAnalyzer fileAnalyzer = new FileAnalyzer();
    private final GoogleDriveAuth auth = new GoogleDriveAuth();

    /**
     * Scans the signed-in user's Google Drive and returns metadata for
     * every non-trashed file.
     *
     * @param isCancelled checked between pages and while processing each
     *                    page; when it returns true the scan stops early
     *                    and throws {@link ScanCancelledException} (nullable — never cancels)
     */
    public List<FileRecord> scan(Consumer<Integer> progressCallback, BooleanSupplier isCancelled) throws Exception {
        Drive driveService = auth.getDriveService();
        List<FileRecord> results = new ArrayList<>();
        String pageToken = null;
        int count = 0;

        do {
            if (isCancelled != null && isCancelled.getAsBoolean()) {
                throw new ScanCancelledException();
            }

            FileList result = driveService.files().list()
                    .setQ("trashed = false")
                    .setSpaces("drive")
                    .setFields(FIELDS)
                    .setPageSize(PAGE_SIZE)
                    .setPageToken(pageToken)
                    .execute();

            for (File driveFile : result.getFiles()) {
                if (isCancelled != null && isCancelled.getAsBoolean()) {
                    throw new ScanCancelledException();
                }
                results.add(toFileRecord(driveFile));
                count++;
                if (progressCallback != null && count % 100 == 0) {
                    progressCallback.accept(count);
                }
            }

            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        if (progressCallback != null) {
            progressCallback.accept(count);
        }

        return results;
    }

    public List<FileRecord> scan(Consumer<Integer> progressCallback) throws Exception {
        return scan(progressCallback, null);
    }

    private FileRecord toFileRecord(File driveFile) {
        String name = driveFile.getName() != null ? driveFile.getName() : "(untitled)";
        long size = driveFile.getSize() != null ? driveFile.getSize() : 0L; // null for Google-native docs
        String extension = extractExtension(name);
        String category = fileAnalyzer.classifyByMimeType(driveFile.getMimeType(), extension);

        LocalDateTime created = toLocalDateTime(driveFile.getCreatedTime());
        LocalDateTime modified = toLocalDateTime(driveFile.getModifiedTime());

        FileRecord record = new FileRecord(name, "drive://" + driveFile.getId(), size, extension,
                created, modified, category);
        record.setSource("Google Drive");
        record.setCloudFileId(driveFile.getId());

        // Trust Drive's own checksum instead of downloading the file to hash it locally.
        if (driveFile.getMd5Checksum() != null) {
            record.setSha256Hash(driveFile.getMd5Checksum());
        }

        return record;
    }

    private LocalDateTime toLocalDateTime(DateTime dateTime) {
        if (dateTime == null) return LocalDateTime.now();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTime.getValue()), ZoneId.systemDefault());
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    public GoogleDriveAuth getAuth() {
        return auth;
    }
}
