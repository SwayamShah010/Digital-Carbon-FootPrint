package com.carbonauditor.scanner;

import com.carbonauditor.model.FileRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileScannerTest {

    private final FileScanner scanner = new FileScanner();

    @Test
    void scan_findsAllFilesInFolder(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        Files.writeString(tempDir.resolve("b.txt"), "world");

        List<FileRecord> files = scanner.scan(tempDir.toString());

        assertEquals(2, files.size());
    }

    @Test
    void scan_recursesIntoSubfolders(@TempDir Path tempDir) throws IOException {
        Path sub = tempDir.resolve("subfolder");
        Files.createDirectory(sub);
        Files.writeString(tempDir.resolve("top.txt"), "top");
        Files.writeString(sub.resolve("nested.txt"), "nested");

        List<FileRecord> files = scanner.scan(tempDir.toString());

        assertEquals(2, files.size());
    }

    @Test
    void scan_nonExistentPath_throwsIOException() {
        assertThrows(IOException.class, () -> scanner.scan("/this/path/does/not/exist/xyz"));
    }

    @Test
    void scan_whenCancelledImmediately_throwsScanCancelledException(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "hello");

        assertThrows(ScanCancelledException.class, () ->
                scanner.scan(tempDir.toString(), null, () -> true));
    }

    @Test
    void scan_whenNotCancelled_completesNormally(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "hello");

        List<FileRecord> files = scanner.scan(tempDir.toString(), null, () -> false);

        assertEquals(1, files.size());
    }

    @Test
    void scan_cancelledPartway_stopsEarlyAndThrows(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 20; i++) {
            Files.writeString(tempDir.resolve("file" + i + ".txt"), "content " + i);
        }

        int[] visitCount = {0};
        assertThrows(ScanCancelledException.class, () ->
                scanner.scan(tempDir.toString(), null, () -> ++visitCount[0] > 5));

        assertTrue(visitCount[0] <= 20, "Cancellation should stop the walk well before visiting every file");
    }
}
