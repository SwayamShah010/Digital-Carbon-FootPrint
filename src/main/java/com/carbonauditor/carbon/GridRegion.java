package com.carbonauditor.carbon;

/**
 * Preset grid carbon-intensity factors for a handful of regions, each with
 * its source cited. Lets the dashboard's region picker swap the carbon
 * intensity factor used by {@link CarbonCalculator} without hard-coding
 * a single country's grid into the estimate.
 *
 * Figures are annual averages and will drift over time as grids
 * decarbonize; treat them as reasonable current snapshots, not constants.
 */
public enum GridRegion {

    GLOBAL_AVERAGE("Global average", 0.445,
            "IEA, \"Electricity 2025\" report — 2024 global average"),
    INDIA("India", 0.710,
            "Central Electricity Authority (CEA) of India, Version 21.0, Dec 2025 — FY2024-25 weighted average emission factor"),
    UNITED_STATES("United States", 0.370,
            "US EPA eGRID-derived national average, ~2024-25 estimates"),
    EUROPEAN_UNION("European Union", 0.230,
            "EU average power-sector carbon intensity, recent industry estimates"),
    FRANCE("France", 0.060,
            "RTE / Electricity Maps — nuclear-heavy grid, among the lowest in Europe"),
    SWEDEN("Sweden", 0.045,
            "Electricity Maps — hydro/nuclear-heavy grid"),
    AUSTRALIA("Australia", 0.660,
            "Australian Government (DCCEEW) National Greenhouse Accounts, recent estimates"),
    CHINA("China", 0.580,
            "IEA / national grid estimates, recent years");

    private final String displayName;
    private final double kgCo2PerKwh;
    private final String source;

    GridRegion(String displayName, double kgCo2PerKwh, String source) {
        this.displayName = displayName;
        this.kgCo2PerKwh = kgCo2PerKwh;
        this.source = source;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getKgCo2PerKwh() {
        return kgCo2PerKwh;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
