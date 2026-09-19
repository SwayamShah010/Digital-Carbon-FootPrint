package com.carbonauditor.analyzer;

import com.carbonauditor.model.FileRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 2: File Classification
 * Categorizes files by extension into Documents, Images, Videos, Audio,
 * Archives, Applications, Temporary files, and Other.
 */
public class FileAnalyzer {

    private static final Set<String> DOCUMENTS = Set.of(
            "doc", "docx", "pdf", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "md");

    private static final Set<String> IMAGES = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "tiff", "heic", "raw");

    private static final Set<String> VIDEOS = Set.of(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg");

    private static final Set<String> AUDIO = Set.of(
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a");

    private static final Set<String> ARCHIVES = Set.of(
            "zip", "rar", "7z", "tar", "gz", "iso", "bz2");

    private static final Set<String> APPLICATIONS = Set.of(
            "exe", "msi", "app", "apk", "dmg", "deb", "rpm", "jar", "bat", "sh");

    private static final Set<String> TEMPORARY = Set.of(
            "tmp", "temp", "cache", "log", "bak", "old", "swp", "part", "crdownload");

    public String classify(String extension) {
        if (extension == null || extension.isBlank()) {
            return "Other";
        }
        String ext = extension.toLowerCase();
        if (TEMPORARY.contains(ext)) return "Temporary";
        if (DOCUMENTS.contains(ext)) return "Documents";
        if (IMAGES.contains(ext)) return "Images";
        if (VIDEOS.contains(ext)) return "Videos";
        if (AUDIO.contains(ext)) return "Audio";
        if (ARCHIVES.contains(ext)) return "Archives";
        if (APPLICATIONS.contains(ext)) return "Applications";
        return "Other";
    }

    public boolean isTemporaryExtension(String extension) {
        return extension != null && TEMPORARY.contains(extension.toLowerCase());
    }

    /**
     * Classifies a cloud file by its MIME type, for sources (like Google
     * Drive) where native files (Docs, Sheets, Slides) have no file
     * extension to classify by. Falls back to {@link #classify(String)}
     * for anything with a recognizable file extension.
     */
    public String classifyByMimeType(String mimeType, String extension) {
        if (mimeType != null) {
            if (mimeType.equals("application/vnd.google-apps.document")) return "Documents";
            if (mimeType.equals("application/vnd.google-apps.spreadsheet")) return "Documents";
            if (mimeType.equals("application/vnd.google-apps.presentation")) return "Documents";
            if (mimeType.equals("application/vnd.google-apps.folder")) return "Other";
            if (mimeType.startsWith("image/")) return "Images";
            if (mimeType.startsWith("video/")) return "Videos";
            if (mimeType.startsWith("audio/")) return "Audio";
            if (mimeType.equals("application/pdf")) return "Documents";
            if (mimeType.contains("zip") || mimeType.contains("compressed")) return "Archives";
        }
        return classify(extension);
    }

    /**
     * Aggregates total storage used per category, e.g. Documents -> 2.4 GB.
     */
    public Map<String, Long> aggregateByCategory(List<FileRecord> files) {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (FileRecord file : files) {
            byCategory.merge(file.getCategory(), file.getFileSize(), Long::sum);
        }
        return byCategory;
    }

    public long totalStorage(List<FileRecord> files) {
        long total = 0;
        for (FileRecord file : files) {
            total += file.getFileSize();
        }
        return total;
    }
}
