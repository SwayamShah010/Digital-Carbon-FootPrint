package com.carbonauditor.carbon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridRegionTest {

    @Test
    void allRegionsHavePositiveIntensityAndSource() {
        for (GridRegion region : GridRegion.values()) {
            assertTrue(region.getKgCo2PerKwh() > 0, region.name() + " should have a positive intensity factor");
            assertFalse(region.getSource().isBlank(), region.name() + " should cite a source");
            assertFalse(region.getDisplayName().isBlank());
        }
    }

    @Test
    void globalAverageMatchesCarbonCalculatorDefault() {
        assertEquals(CarbonCalculator.DEFAULT_CARBON_INTENSITY_FACTOR_KG_PER_KWH,
                GridRegion.GLOBAL_AVERAGE.getKgCo2PerKwh(), 0.0001);
    }

    @Test
    void franceAndSweden_areAmongTheLowestIntensity() {
        double max = 0;
        for (GridRegion region : GridRegion.values()) {
            max = Math.max(max, region.getKgCo2PerKwh());
        }
        assertTrue(GridRegion.FRANCE.getKgCo2PerKwh() < max);
        assertTrue(GridRegion.SWEDEN.getKgCo2PerKwh() < max);
    }

    @Test
    void toString_returnsDisplayName() {
        assertEquals(GridRegion.INDIA.getDisplayName(), GridRegion.INDIA.toString());
    }
}
