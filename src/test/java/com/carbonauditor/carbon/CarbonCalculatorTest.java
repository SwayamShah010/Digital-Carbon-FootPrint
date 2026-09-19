package com.carbonauditor.carbon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarbonCalculatorTest {

    private static final double DELTA = 0.0001;

    @Test
    void estimateCarbonKg_usesConfiguredFactors() {
        CarbonCalculator calc = new CarbonCalculator(0.1, 0.445);
        // 100 GB * 0.1 kWh/GB/year * 0.445 kg/kWh = 4.45 kg
        assertEquals(4.45, calc.estimateCarbonKg(100), DELTA);
    }

    @Test
    void estimateCarbonKg_zeroStorage_isZero() {
        CarbonCalculator calc = new CarbonCalculator();
        assertEquals(0.0, calc.estimateCarbonKg(0), DELTA);
    }

    @Test
    void estimatePotentialSavingsKg_matchesEstimateCarbonKg() {
        CarbonCalculator calc = new CarbonCalculator(0.2, 0.5);
        assertEquals(calc.estimateCarbonKg(50), calc.estimatePotentialSavingsKg(50), DELTA);
    }

    @Test
    void estimateReductionPercent_computesCorrectPercentage() {
        CarbonCalculator calc = new CarbonCalculator();
        assertEquals(25.0, calc.estimateReductionPercent(200, 50), DELTA);
    }

    @Test
    void estimateReductionPercent_zeroTotalStorage_returnsZero() {
        CarbonCalculator calc = new CarbonCalculator();
        assertEquals(0.0, calc.estimateReductionPercent(0, 50), DELTA);
    }

    @Test
    void settersOverrideDefaults() {
        CarbonCalculator calc = new CarbonCalculator();
        calc.setEnergyUsageFactorKwhPerGbYear(1.0);
        calc.setCarbonIntensityFactorKgPerKwh(1.0);
        assertEquals(10.0, calc.estimateCarbonKg(10), DELTA);
    }

    @Test
    void kgToEquivalentKmDriven_isPositiveAndProportional() {
        CarbonCalculator calc = new CarbonCalculator();
        double km10 = calc.kgToEquivalentKmDriven(10);
        double km20 = calc.kgToEquivalentKmDriven(20);
        assertTrue(km10 > 0);
        assertEquals(km20, km10 * 2, DELTA);
    }

    @Test
    void defaultFactorsMatchPubliclyDocumentedConstants() {
        assertEquals(CarbonCalculator.DEFAULT_ENERGY_USAGE_FACTOR_KWH_PER_GB_YEAR, 0.1, DELTA);
        assertEquals(CarbonCalculator.DEFAULT_CARBON_INTENSITY_FACTOR_KG_PER_KWH, 0.445, DELTA);
    }

    @Test
    void kgToEquivalentPhoneCharges_isPositiveAndProportional() {
        CarbonCalculator calc = new CarbonCalculator();
        double c10 = calc.kgToEquivalentPhoneCharges(10);
        double c20 = calc.kgToEquivalentPhoneCharges(20);
        assertTrue(c10 > 0);
        assertEquals(c20, c10 * 2, DELTA);
    }

    @Test
    void methodologyNote_isNotEmptyAndMentionsFactors() {
        CarbonCalculator calc = new CarbonCalculator();
        String note = calc.getMethodologyNote();
        assertTrue(note != null && !note.isBlank());
        assertTrue(note.contains("kWh/GB/year"));
    }
}
