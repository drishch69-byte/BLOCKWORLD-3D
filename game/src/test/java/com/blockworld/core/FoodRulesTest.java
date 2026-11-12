package com.blockworld.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodRulesTest {
    @Test void cookedFishRestoresMoreHungerThanBread() {
        assertEquals(15f, FoodRules.restoreHunger(5f, FoodRules.COOKED_FISH_RESTORATION));
        assertEquals(13f, FoodRules.restoreHunger(5f, FoodRules.BREAD_RESTORATION));
    }

    @Test void restorationNeverExceedsMaximumOrFallsBelowZero() {
        assertEquals(20f, FoodRules.restoreHunger(18f, 10f));
        assertEquals(0f, FoodRules.restoreHunger(-4f, Float.NaN));
    }
}
