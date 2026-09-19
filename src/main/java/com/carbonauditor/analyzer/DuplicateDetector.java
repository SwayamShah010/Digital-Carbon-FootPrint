package com.carbonauditor.analyzer;

import com.carbonauditor.model.FileRecord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module 7: Duplicate File Detection
 *
 * Two-step process:
 *   Step 1 - Compare file size (files of different sizes cannot be duplicates)
 *   Step 2 - Calculate SHA-256 hash for files that share a size; identical
 *            hashes mean the files are duplicates.
 */
public class DuplicateDetector {

    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    /**
     * Groups files by size, hashes only the files that share a size with
     * at least one other file, and returns groups of confirmed duplicates
     * (size 2+). Also marks each FileRecord's duplicate flag and hash.
     */
    public List<List<FileRecord>> findDuplicates(List<FileRecord> files) {
        // Step 1: bucket by file size
        Map<Long, List<FileRecord>> bySize = new HashMap<>();
        for (FileRecord file : files) {
            if (file.getFileSize() == 0) continue; // ignore empty files
            bySize.computeIfAbsent(file.getFileSize(), k -> new ArrayList<>()).add(file);
        }

        List<List<FileRecord>> duplicateGroups = new ArrayList<>();

        for (List<FileRecord> sameSizeFiles : bySize.values()) {
            if (sameSizeFiles.size() < 2) continue; // unique size, cannot be a duplicate

            // Step 2: hash files that share a size
            Map<String, List<FileRecord>> byHash = new HashMap<>();
            for (FileRecord file : sameSizeFiles) {
                try {
                    String hash = computeSha256(Path.of(file.getFilePath()));
                    file.setSha256Hash(hash);
                    byHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                } catch (IOException | NoSuchAlgorithmException e) {
                    // Unreadable file: skip hashing, cannot confirm duplicate status
                }
            }

            for (List<FileRecord> group : byHash.values()) {
                if (group.size() >= 2) {
                    for (FileRecord f : group) {
                        f.setDuplicate(true);
                    }
                    duplicateGroups.add(group);
                }
            }
        }

        return duplicateGroups;
    }

    private String computeSha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Same grouping logic as {@link #findDuplicates(List)}, but for files
     * whose hash is already known (e.g. Google Drive's md5Checksum,
     * supplied by the API) rather than computed by reading local bytes.
     * Used for cloud sources where downloading every file just to hash it
     * would be slow and wasteful — the provider's own checksum is trusted
     * instead.
     */
    public List<List<FileRecord>> findDuplicatesUsingProvidedHashes(List<FileRecord> files) {
        Map<Long, List<FileRecord>> bySize = new HashMap<>();
        for (FileRecord file : files) {
            if (file.getFileSize() == 0 || file.getSha256Hash() == null || file.getSha256Hash().isBlank()) {
                continue; // no usable checksum (e.g. Google-native Docs/Sheets have no fixed byte content)
            }
            bySize.computeIfAbsent(file.getFileSize(), k -> new ArrayList<>()).add(file);
        }

        List<List<FileRecord>> duplicateGroups = new ArrayList<>();
        for (List<FileRecord> sameSizeFiles : bySize.values()) {
            if (sameSizeFiles.size() < 2) continue;

            Map<String, List<FileRecord>> byHash = new HashMap<>();
            for (FileRecord file : sameSizeFiles) {
                byHash.computeIfAbsent(file.getSha256Hash(), k -> new ArrayList<>()).add(file);
            }

            for (List<FileRecord> group : byHash.values()) {
                if (group.size() >= 2) {
                    for (FileRecord f : group) {
                        f.setDuplicate(true);
                    }
                    duplicateGroups.add(group);
                }
            }
        }

        return duplicateGroups;
    }

    public long totalDuplicateStorage(List<List<FileRecord>> duplicateGroups) {
        long total = 0;
        for (List<FileRecord> group : duplicateGroups) {
            // Keep one copy "free"; everything past the first is reclaimable
            for (int i = 1; i < group.size(); i++) {
                total += group.get(i).getFileSize();
            }
        }
        return total;
    }
}
