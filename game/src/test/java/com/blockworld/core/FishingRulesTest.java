package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FishingRulesTest {
    @Test
    void catchChanceHasExpectedBoundary() {
        assertTrue(FishingRules.catchesFish(0f));
        assertTrue(FishingRules.catchesFish(0.71f));
        assertFalse(FishingRules.catchesFish(0.72f));
        assertFalse(FishingRules.catchesFish(0.99f));
    }

    @Test
    void cooldownAdvancesWithoutGoingBelowZero() {
        assertEquals(1.5f, FishingRules.advanceCooldown(2.5f, 1f), 0.0001f);
        assertEquals(0f, FishingRules.advanceCooldown(0.2f, 1f), 0.0001f);
        assertEquals(2f, FishingRules.advanceCooldown(2f, Float.NaN), 0.0001f);
    }

    @Test
    void castRangeMatchesTheGameRayDistance() {
        assertEquals(6.0f, FishingRules.CAST_RANGE, 0.0001f);
    }

    @Test
    void invalidRandomRollIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FishingRules.catchesFish(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> FishingRules.catchesFish(1f));
    }
}
