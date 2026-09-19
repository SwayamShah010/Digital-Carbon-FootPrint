package com.carbonauditor.recommendation;

import com.carbonauditor.model.FileRecord;
import com.carbonauditor.model.Recommendation;
import com.carbonauditor.model.Recommendation.Priority;
import com.carbonauditor.model.ScanResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Module 10: Recommendation Engine
 *
 * Turns scan results into structured, actionable recommendations, each with
 * a priority, a plain-English explanation, a concrete suggested action, and
 * the storage it would free up. Sorted so the highest-impact items surface
 * first.
 */
public class RecommendationEngine {

    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    public List<Recommendation> generateRecommendations(ScanResult result) {
        List<Recommendation> recs = new ArrayList<>();

        long duplicateCount = result.getAllFiles().stream().filter(FileRecord::isDuplicate).count();
        double duplicateGb = result.getDuplicateStorageBytes() / GB;
        if (duplicateCount > 0) {
            recs.add(new Recommendation(
                    Priority.HIGH,
                    "Remove duplicate files",
                    String.format("%d duplicate files were found across %d groups, using about %.1f GB of extra space.",
                            duplicateCount, result.getDuplicateGroups().size(), duplicateGb),
                    "Open the Files to Review tab, sort by duplicates, and delete extra copies — keep one file per group.",
                    duplicateGb
            ));
        }

        long tempCount = result.getAllFiles().stream().filter(FileRecord::isTemporary).count();
        double tempGb = result.getTemporaryStorageBytes() / GB;
        if (tempCount > 0) {
            recs.add(new Recommendation(
                    Priority.HIGH,
                    "Clear temporary and cache files",
                    String.format("%d temporary/cache/log files are consuming about %.1f GB.", tempCount, tempGb),
                    "These are generally safe to delete — review the list and remove them to reclaim space immediately.",
                    tempGb
            ));
        }

        long largeCount = result.getAllFiles().stream().filter(FileRecord::isLarge).count();
        double largeGb = result.getLargeFileStorageBytes() / GB;
        if (largeCount > 0) {
            recs.add(new Recommendation(
                    Priority.MEDIUM,
                    "Review large files",
                    String.format("%d files are larger than 500 MB, totaling about %.1f GB.", largeCount, largeGb),
                    "Check whether these are still needed, or move them to external/archival storage.",
                    largeGb
            ));
        }

        long oldCount = result.getAllFiles().stream().filter(FileRecord::isOld).count();
        double oldGb = result.getOldFileStorageBytes() / GB;
        if (oldCount > 0) {
            recs.add(new Recommendation(
                    Priority.MEDIUM,
                    "Archive files you haven't touched in over a year",
                    String.format("%d files haven't been modified in more than a year, using about %.1f GB.", oldCount, oldGb),
                    "Consider archiving to cold/cloud storage rather than keeping them on primary storage.",
                    oldGb
            ));
        }

        if (result.getPotentialCarbonSavingsKg() > 0) {
            recs.add(new Recommendation(
                    Priority.INFO,
                    "Estimated impact of cleaning up",
                    String.format("Acting on the items above could reduce your estimated digital carbon footprint by about %.1f kg CO2e/year (%.0f%% of current storage).",
                            result.getPotentialCarbonSavingsKg(), result.getPotentialStorageReductionPercent()),
                    "Carbon figures are approximate estimates based on average energy and grid-intensity factors, not exact emissions.",
                    result.getPotentialCleanupGB()
            ));
        }

        if (recs.isEmpty()) {
            recs.add(new Recommendation(
                    Priority.INFO,
                    "Looks clean",
                    "No significant duplicate, old, large, or temporary files were found in this scan.",
                    "No action needed right now.",
                    0
            ));
        }

        recs.sort(Comparator
                .comparing((Recommendation r) -> priorityRank(r.getPriority()))
                .thenComparing(Recommendation::getPotentialSavingsGb, Comparator.reverseOrder()));

        return recs;
    }

    private int priorityRank(Priority p) {
        return switch (p) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case INFO -> 3;
        };
    }

    /** Flattened plain-text form, used only for database persistence. */
    public List<String> toPlainText(List<Recommendation> recommendations) {
        List<String> lines = new ArrayList<>();
        for (Recommendation r : recommendations) {
            lines.add("[" + r.getPriority() + "] " + r.getTitle() + " — " + r.getDetail() + " " + r.getAction());
        }
        return lines;
    }
}
