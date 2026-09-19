package com.carbonauditor.model;

/**
 * A single structured, actionable recommendation surfaced to the user.
 * Replaces plain strings so the UI can render priority badges, group by
 * category, and sort by impact instead of just printing a flat list.
 */
public class Recommendation {

    public enum Priority {
        HIGH, MEDIUM, LOW, INFO
    }

    private final Priority priority;
    private final String title;       // e.g. "Remove duplicate files"
    private final String detail;      // e.g. "128 duplicate files are taking up 4.2 GB."
    private final String action;      // e.g. "Review and delete extra copies, keeping one of each."
    private final double potentialSavingsGb;

    public Recommendation(Priority priority, String title, String detail, String action, double potentialSavingsGb) {
        this.priority = priority;
        this.title = title;
        this.detail = detail;
        this.action = action;
        this.potentialSavingsGb = potentialSavingsGb;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getAction() {
        return action;
    }

    public double getPotentialSavingsGb() {
        return potentialSavingsGb;
    }
}
