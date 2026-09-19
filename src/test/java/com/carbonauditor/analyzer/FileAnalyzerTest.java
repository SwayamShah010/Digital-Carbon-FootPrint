package com.carbonauditor.analyzer;

import com.carbonauditor.model.FileRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileAnalyzerTest {

    private final FileAnalyzer analyzer = new FileAnalyzer();

    @Test
    void classifiesDocumentExtensions() {
        assertEquals("Documents", analyzer.classify("pdf"));
        assertEquals("Documents", analyzer.classify("DOCX")); // case-insensitive
    }

    @Test
    void classifiesImageExtensions() {
        assertEquals("Images", analyzer.classify("png"));
    }

    @Test
    void classifiesTemporaryExtensionsBeforeOtherCategories() {
        assertEquals("Temporary", analyzer.classify("tmp"));
        assertEquals("Temporary", analyzer.classify("log"));
    }

    @Test
    void unknownExtension_classifiedAsOther() {
        assertEquals("Other", analyzer.classify("xyz123"));
    }

    @Test
    void blankExtension_classifiedAsOther() {
        assertEquals("Other", analyzer.classify(""));
        assertEquals("Other", analyzer.classify(null));
    }

    @Test
    void classifiesGoogleNativeDocsByMimeType() {
        assertEquals("Documents", analyzer.classifyByMimeType("application/vnd.google-apps.document", ""));
        assertEquals("Documents", analyzer.classifyByMimeType("application/vnd.google-apps.spreadsheet", ""));
        assertEquals("Documents", analyzer.classifyByMimeType("application/vnd.google-apps.presentation", ""));
    }

    @Test
    void classifiesCloudImagesAndVideosByMimeType() {
        assertEquals("Images", analyzer.classifyByMimeType("image/jpeg", "jpg"));
        assertEquals("Videos", analyzer.classifyByMimeType("video/mp4", "mp4"));
    }

    @Test
    void classifyByMimeType_fallsBackToExtensionWhenMimeTypeUnhelpful() {
        assertEquals("Documents", analyzer.classifyByMimeType("application/octet-stream", "pdf"));
    }

    @Test
    void aggregateByCategory_sumsSizesPerCategory() {
        FileRecord doc = new FileRecord("a.pdf", "/a.pdf", 100, "pdf", LocalDateTime.now(), LocalDateTime.now(), "Documents");
        FileRecord img = new FileRecord("b.png", "/b.png", 200, "png", LocalDateTime.now(), LocalDateTime.now(), "Images");
        FileRecord doc2 = new FileRecord("c.pdf", "/c.pdf", 50, "pdf", LocalDateTime.now(), LocalDateTime.now(), "Documents");

        Map<String, Long> byCategory = analyzer.aggregateByCategory(List.of(doc, img, doc2));

        assertEquals(150L, byCategory.get("Documents"));
        assertEquals(200L, byCategory.get("Images"));
    }

    @Test
    void totalStorage_sumsAllFileSizes() {
        FileRecord a = new FileRecord("a", "/a", 100, "txt", LocalDateTime.now(), LocalDateTime.now(), "Other");
        FileRecord b = new FileRecord("b", "/b", 250, "txt", LocalDateTime.now(), LocalDateTime.now(), "Other");
        assertEquals(350L, analyzer.totalStorage(List.of(a, b)));
    }
}
