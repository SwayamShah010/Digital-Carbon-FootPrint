package com.carbonauditor.analyzer;

import com.carbonauditor.model.FileRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateDetectorTest {

    private final DuplicateDetector detector = new DuplicateDetector();

    private FileRecord recordFor(Path path) throws IOException {
        long size = Files.size(path);
        return new FileRecord(path.getFileName().toString(), path.toAbsolutePath().toString(),
                size, "txt", LocalDateTime.now(), LocalDateTime.now(), "Documents");
    }

    @Test
    void identicalContent_isDetectedAsDuplicate(@TempDir Path tempDir) throws IOException {
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Files.writeString(a, "identical content for testing");
        Files.writeString(b, "identical content for testing");

        List<FileRecord> files = new ArrayList<>(List.of(recordFor(a), recordFor(b)));
        List<List<FileRecord>> groups = detector.findDuplicates(files);

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).size());
        assertTrue(files.get(0).isDuplicate());
        assertTrue(files.get(1).isDuplicate());
    }

    @Test
    void differentContent_sameSize_isNotDuplicate(@TempDir Path tempDir) throws IOException {
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Files.writeString(a, "aaaaaaaaaa"); // 10 bytes
        Files.writeString(b, "bbbbbbbbbb"); // 10 bytes, same size, different content

        List<FileRecord> files = new ArrayList<>(List.of(recordFor(a), recordFor(b)));
        List<List<FileRecord>> groups = detector.findDuplicates(files);

        assertTrue(groups.isEmpty());
        assertFalse(files.get(0).isDuplicate());
        assertFalse(files.get(1).isDuplicate());
    }

    @Test
    void differentSizes_neverHashed_notDuplicate(@TempDir Path tempDir) throws IOException {
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Files.writeString(a, "short");
        Files.writeString(b, "a much longer piece of content than the other file");

        List<FileRecord> files = new ArrayList<>(List.of(recordFor(a), recordFor(b)));
        List<List<FileRecord>> groups = detector.findDuplicates(files);

        assertTrue(groups.isEmpty());
    }

    @Test
    void emptyFiles_areIgnored(@TempDir Path tempDir) throws IOException {
        Path a = tempDir.resolve("empty1.txt");
        Path b = tempDir.resolve("empty2.txt");
        Files.createFile(a);
        Files.createFile(b);

        List<FileRecord> files = new ArrayList<>(List.of(recordFor(a), recordFor(b)));
        List<List<FileRecord>> groups = detector.findDuplicates(files);

        assertTrue(groups.isEmpty(), "Empty files should not be flagged as duplicates of each other");
    }

    @Test
    void threeIdenticalFiles_formOneGroupOfThree(@TempDir Path tempDir) throws IOException {
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Path c = tempDir.resolve("c.txt");
        String content = "triplicate content";
        Files.writeString(a, content);
        Files.writeString(b, content);
        Files.writeString(c, content);

        List<FileRecord> files = new ArrayList<>(List.of(recordFor(a), recordFor(b), recordFor(c)));
        List<List<FileRecord>> groups = detector.findDuplicates(files);

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).size());
    }

    @Test
    void totalDuplicateStorage_countsAllButOneCopyPerGroup(@TempDir Path tempDir) throws IOException {
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Path c = tempDir.resolve("c.txt");
        String content = "abc"; // 3 bytes each
        Files.writeString(a, content);
        Files.writeString(b, content);
        Files.writeString(c, content);

        List<FileRecord> files = new ArrayList<>(List.of(recordFor(a), recordFor(b), recordFor(c)));
        List<List<FileRecord>> groups = detector.findDuplicates(files);

        // 3 files of 3 bytes each; 1 kept "free", 2 reclaimable => 6 bytes
        assertEquals(6, detector.totalDuplicateStorage(groups));
    }

    // --- Cloud-source (provided-hash) duplicate detection ---

    private FileRecord cloudRecord(String name, long size, String md5) {
        FileRecord f = new FileRecord(name, "drive://fake-" + name, size, "",
                LocalDateTime.now(), LocalDateTime.now(), "Documents");
        f.setSource("Google Drive");
        f.setSha256Hash(md5);
        return f;
    }

    @Test
    void providedHashes_sameHashSameSize_isDuplicate() {
        List<FileRecord> files = new ArrayList<>(List.of(
                cloudRecord("a.pdf", 500, "abc123"),
                cloudRecord("b.pdf", 500, "abc123")
        ));

        List<List<FileRecord>> groups = detector.findDuplicatesUsingProvidedHashes(files);

        assertEquals(1, groups.size());
        assertTrue(files.get(0).isDuplicate());
        assertTrue(files.get(1).isDuplicate());
    }

    @Test
    void providedHashes_differentHash_notDuplicate() {
        List<FileRecord> files = new ArrayList<>(List.of(
                cloudRecord("a.pdf", 500, "abc123"),
                cloudRecord("b.pdf", 500, "xyz789")
        ));

        List<List<FileRecord>> groups = detector.findDuplicatesUsingProvidedHashes(files);

        assertTrue(groups.isEmpty());
    }

    @Test
    void providedHashes_googleNativeFileWithNoChecksum_isSkipped() {
        FileRecord googleDoc = cloudRecord("Untitled document", 0, null);
        FileRecord another = cloudRecord("Another doc", 0, null);

        List<FileRecord> files = new ArrayList<>(List.of(googleDoc, another));
        List<List<FileRecord>> groups = detector.findDuplicatesUsingProvidedHashes(files);

        assertTrue(groups.isEmpty(), "Files with no checksum (e.g. Google-native docs) should never be flagged as duplicates");
    }
}
