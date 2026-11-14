package com.blockworld.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmRulesTest {
    @Test void cropProgressStopsAtMaturity() {
        assertEquals(30f, FarmRules.advance(10f, 20f));
        assertEquals(FarmRules.MATURATION_SECONDS, FarmRules.advance(44f, 3f));
        assertTrue(FarmRules.isMature(FarmRules.MATURATION_SECONDS));
        assertFalse(FarmRules.isMature(44.9f));
    }

    @Test void rainAndNearbyFarmerSpeedCropGrowth() {
        float normal = FarmRules.advance(10f, 10f, false, false);
        float rain = FarmRules.advance(10f, 10f, true, false);
        float farmer = FarmRules.advance(10f, 10f, false, true);
        assertTrue(rain > normal);
        assertTrue(farmer > normal);
        float animal = FarmRules.advance(10f, 10f, false, false, true);
        assertTrue(animal > normal);
        assertEquals(FarmRules.MATURATION_SECONDS,
                FarmRules.advance(44f, 20f, true, true, true));
    }

    @Test void harvestSeedCountIsBounded() {
        assertEquals(1, FarmRules.harvestSeeds(0));
        assertEquals(2, FarmRules.harvestSeeds(2));
        assertEquals(3, FarmRules.harvestSeeds(9));
    }
}
