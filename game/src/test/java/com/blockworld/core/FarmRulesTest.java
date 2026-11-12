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

    @Test void harvestSeedCountIsBounded() {
        assertEquals(1, FarmRules.harvestSeeds(0));
        assertEquals(2, FarmRules.harvestSeeds(2));
        assertEquals(3, FarmRules.harvestSeeds(9));
    }
}
