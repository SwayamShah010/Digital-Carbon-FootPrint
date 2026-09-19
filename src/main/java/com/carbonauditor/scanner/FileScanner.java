package com.carbonauditor.scanner;

import com.carbonauditor.analyzer.FileAnalyzer;
import com.carbonauditor.model.FileRecord;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Module 1: File Scanner
 * Recursively scans a user-selected folder/drive and collects file metadata
 * using java.nio.file APIs.
 */
public class FileScanner {

    // Folders that should never be scanned/recommended for cleanup (safety requirement #21)
    private static final Set<String> EXCLUDED_FOLDER_NAMES = Set.of(
            "Windows", "Program Files", "Program Files (x86)", "System32",
            "$Recycle.Bin", ".git", "node_modules", "System Volume Information"
    );

    private final FileAnalyzer fileAnalyzer = new FileAnalyzer();

    /**
     * Scans the given root path and returns metadata for every regular file found.
     *
     * @param rootPath         folder or drive to scan
     * @param progressCallback optional callback invoked with the count of files scanned so far (nullable)
     * @param isCancelled      checked before/during each file visit; when it returns true the walk stops
     *                         early and {@link ScanCancelledException} is thrown (nullable — never cancels)
     */
    public List<FileRecord> scan(String rootPath, Consumer<Integer> progressCallback, BooleanSupplier isCancelled) throws IOException {
        Path root = Paths.get(rootPath);
        if (!Files.exists(root)) {
            throw new IOException("Path does not exist: " + rootPath);
        }

        List<FileRecord> results = new ArrayList<>();
        final int[] counter = {0};
        final boolean[] cancelled = {false};

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isCancelled != null && isCancelled.getAsBoolean()) {
                    cancelled[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (EXCLUDED_FOLDER_NAMES.contains(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isCancelled != null && isCancelled.getAsBoolean()) {
                    cancelled[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                try {
                    FileRecord record = buildFileRecord(file, attrs);
                    results.add(record);
                    counter[0]++;
                    if (progressCallback != null && counter[0] % 100 == 0) {
                        progressCallback.accept(counter[0]);
                    }
                } catch (Exception e) {
                    // Skip unreadable files silently (e.g. permission errors) and continue scanning
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Permission errors / broken links: skip and keep going
                return FileVisitResult.CONTINUE;
            }
        });

        if (cancelled[0]) {
            throw new ScanCancelledException();
        }

        if (progressCallback != null) {
            progressCallback.accept(counter[0]);
        }

        return results;
    }

    public List<FileRecord> scan(String rootPath, Consumer<Integer> progressCallback) throws IOException {
        return scan(rootPath, progressCallback, null);
    }

    public List<FileRecord> scan(String rootPath) throws IOException {
        return scan(rootPath, null, null);
    }

    private FileRecord buildFileRecord(Path file, BasicFileAttributes attrs) {
        String fileName = file.getFileName() != null ? file.getFileName().toString() : file.toString();
        String extension = extractExtension(fileName);
        long size = attrs.size();

        LocalDateTime created = LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
        LocalDateTime modified = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

        String category = fileAnalyzer.classify(extension);

        return new FileRecord(fileName, file.toAbsolutePath().toString(), size, extension, created, modified, category);
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }
}
