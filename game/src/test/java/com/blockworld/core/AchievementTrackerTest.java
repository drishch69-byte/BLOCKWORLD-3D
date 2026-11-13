package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AchievementTrackerTest {
    @Test
    void achievementsUnlockOnlyOnce() {
        AchievementTracker tracker = new AchievementTracker();
        assertTrue(tracker.unlock(Achievement.FIRST_FISH));
        assertFalse(tracker.unlock(Achievement.FIRST_FISH));
        assertEquals(1, tracker.unlockedCount());
    }

    @Test
    void legacyProfilesDoNotRequireTheNewBiomesForTheBiomeAchievement() {
        AchievementTracker tracker = new AchievementTracker();
        tracker.setExtendedBiomeSet(false);
        for (BiomeType biome : BiomeType.legacySet()) tracker.discover(biome);
        assertTrue(tracker.hasDiscoveredAllBiomes());
        assertTrue(tracker.isUnlocked(Achievement.ALL_BIOMES));

        tracker.setExtendedBiomeSet(true);
        assertFalse(tracker.hasDiscoveredAllBiomes());
    }

    @Test
    void allBiomesCanBeTrackedAndRestored() {
        AchievementTracker tracker = new AchievementTracker();
        for (BiomeType biome : BiomeType.values()) assertTrue(tracker.discover(biome));
        assertTrue(tracker.hasDiscoveredAllBiomes());
        assertEquals(BiomeType.values().length, tracker.discoveredBiomeCount());

        AchievementTracker restored = new AchievementTracker();
        restored.restore(tracker.unlocked(), tracker.discoveredBiomes());
        assertTrue(restored.hasDiscoveredAllBiomes());
    }
}
