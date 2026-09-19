package com.carbonauditor.carbon;

/**
 * Module 9 &amp; 11: Carbon Footprint Estimator / Carbon Savings Calculator
 *
 * Simplified conceptual model:
 *   Estimated Carbon Footprint = Stored Data (GB) x Energy Usage Factor x Carbon Intensity Factor
 *
 * Both factors are configurable and sourced from published figures so the
 * result can be presented as a transparent, defensible estimate rather
 * than an arbitrary number. The result should still be read as an
 * order-of-magnitude estimate, not an exact emissions measurement — see
 * {@link #getMethodologyNote()} for the caveats shown to the user.
 *
 * SOURCES (see README.md "Configuring the Carbon Model" for full citations):
 *
 * Energy Usage Factor (kWh per GB stored, per year):
 *   Default: 0.1 kWh/GB/year — a modern estimate for cloud/data-center
 *   storage after efficiency improvements (AI Monks, "The Hidden Cost of
 *   the Cloud", 2026, citing recent industry analyses). Older, higher
 *   estimates of 3-7 kWh/GB/year exist (Carnegie Mellon University study,
 *   cited in Stanford Magazine, "Carbon and the Cloud") but are now widely
 *   considered outdated given data-center efficiency gains.
 *   NOTE: this app scans *local* storage. Local-disk energy use alone is
 *   far lower (~0.000005 kWh/GB, per the same Stanford Magazine article) —
 *   the cloud/data-center figure is used deliberately here because most
 *   personal files are backed up or synced to cloud storage in practice,
 *   so it better approximates a user's real end-to-end footprint. This
 *   assumption is stated explicitly in the UI so it is never presented as
 *   a precise, unconditional figure.
 *
 * Carbon Intensity Factor (kg CO2 per kWh, grid average):
 *   Default: 0.445 kg CO2/kWh — the IEA's 2024 global average power-sector
 *   carbon intensity (IEA, "Electricity 2025" report, ~445 gCO2/kWh in 2024,
 *   trending down to ~400 gCO2/kWh by 2027). Grid intensity varies a lot by
 *   country (e.g. India's CEA-published weighted average emission factor
 *   for FY2024-25 is 0.710 kg CO2/kWh; Sweden and France are far lower due
 *   to nuclear/hydro) so this factor is intentionally configurable per
 *   region rather than hard-coded to one country.
 */
public class CarbonCalculator {

    /** Default: 0.1 kWh consumed per GB of stored data per year (see class javadoc for source). */
    public static final double DEFAULT_ENERGY_USAGE_FACTOR_KWH_PER_GB_YEAR = 0.1;

    /** Default: IEA 2024 global average grid carbon intensity, kg CO2 per kWh (see class javadoc for source). */
    public static final double DEFAULT_CARBON_INTENSITY_FACTOR_KG_PER_KWH = 0.445;

    /** kWh consumed per GB of stored data per year (storage + backup/sync infra). */
    private double energyUsageFactorKwhPerGbYear;

    /** kg CO2 emitted per kWh of electricity consumed (grid carbon intensity). */
    private double carbonIntensityFactorKgPerKwh;

    public CarbonCalculator() {
        this(DEFAULT_ENERGY_USAGE_FACTOR_KWH_PER_GB_YEAR, DEFAULT_CARBON_INTENSITY_FACTOR_KG_PER_KWH);
    }

    public CarbonCalculator(double energyUsageFactorKwhPerGbYear, double carbonIntensityFactorKgPerKwh) {
        this.energyUsageFactorKwhPerGbYear = energyUsageFactorKwhPerGbYear;
        this.carbonIntensityFactorKgPerKwh = carbonIntensityFactorKgPerKwh;
    }

    public double getEnergyUsageFactorKwhPerGbYear() {
        return energyUsageFactorKwhPerGbYear;
    }

    public void setEnergyUsageFactorKwhPerGbYear(double factor) {
        this.energyUsageFactorKwhPerGbYear = factor;
    }

    public double getCarbonIntensityFactorKgPerKwh() {
        return carbonIntensityFactorKgPerKwh;
    }

    public void setCarbonIntensityFactorKgPerKwh(double factor) {
        this.carbonIntensityFactorKgPerKwh = factor;
    }

    /**
     * Estimates yearly carbon emissions (kg CO2e/year) for the given amount
     * of stored data.
     */
    public double estimateCarbonKg(double storageGb) {
        double energyKwh = storageGb * energyUsageFactorKwhPerGbYear;
        return energyKwh * carbonIntensityFactorKgPerKwh;
    }

    /**
     * Estimates the carbon savings (kg CO2e/year) achievable by cleaning up
     * the given amount of storage.
     */
    public double estimatePotentialSavingsKg(double potentialCleanupGb) {
        return estimateCarbonKg(potentialCleanupGb);
    }

    /**
     * Percentage reduction in carbon impact if the potential cleanup is carried out.
     */
    public double estimateReductionPercent(double totalStorageGb, double potentialCleanupGb) {
        if (totalStorageGb <= 0) return 0.0;
        return (potentialCleanupGb / totalStorageGb) * 100.0;
    }

    /**
     * Converts a kg CO2e figure into a relatable real-world equivalent,
     * for display alongside the raw number. Uses the commonly cited
     * EPA/IEA passenger-vehicle average of ~0.121 kg CO2 per km driven.
     */
    public double kgToEquivalentKmDriven(double kgCo2) {
        return kgCo2 / 0.121;
    }

    /**
     * Converts a kg CO2e figure into an equivalent number of smartphone
     * full charges, using a commonly cited ~0.0084 kg CO2 per charge
     * (based on typical smartphone battery capacity and average grid mix).
     */
    public double kgToEquivalentPhoneCharges(double kgCo2) {
        return kgCo2 / 0.0084;
    }

    /** Short, user-facing explanation of the assumptions behind the estimate. */
    public String getMethodologyNote() {
        return String.format(
                "Estimate = storage (GB) x %.2f kWh/GB/year x %.3f kg CO2/kWh. " +
                "Energy factor reflects modern cloud/data-center storage energy use; " +
                "carbon intensity is the IEA global grid average. Actual impact varies " +
                "by provider, region, and whether files are cloud-synced. Treat this as " +
                "an order-of-magnitude estimate, not an exact measurement.",
                energyUsageFactorKwhPerGbYear, carbonIntensityFactorKgPerKwh);
    }
}
