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

    @Test
    void enchantedBiomeIsRequiredOnlyForTheNewWorldProfile() {
        AchievementTracker oldProfile = new AchievementTracker();
        oldProfile.setExtendedBiomeSet(true);
        assertEquals(7, oldProfile.requiredBiomeCount());
        for (BiomeType biome : BiomeType.extendedSet()) oldProfile.discover(biome);
        assertTrue(oldProfile.hasDiscoveredAllBiomes());

        AchievementTracker newProfile = new AchievementTracker();
        newProfile.setExtendedBiomeSet(true);
        newProfile.setEnchantedBiomeSet(true);
        assertEquals(8, newProfile.requiredBiomeCount());
        for (BiomeType biome : BiomeType.extendedSet()) newProfile.discover(biome);
        assertFalse(newProfile.hasDiscoveredAllBiomes());
        newProfile.discover(BiomeType.ENCHANTED_GROVE);
        assertTrue(newProfile.hasDiscoveredAllBiomes());
    }


    @Test
    void artifactMilestonesUnlockFromASynchronizedPlayerInventoryOnlyOnce() {
        AchievementTracker tracker = new AchievementTracker();
        var partial = tracker.unlockArtifactMilestones(java.util.Map.of(ItemType.ANCIENT_RELIC, 1));
        assertTrue(partial.contains(Achievement.FIND_ANCIENT_RELIC));
        assertFalse(partial.contains(Achievement.COMPLETE_ARTIFACT_SET));

        var complete = tracker.unlockArtifactMilestones(java.util.Map.of(
                ItemType.ANCIENT_RELIC, 1, ItemType.SUN_SIGIL, 1, ItemType.MOON_SIGIL, 1));
        assertTrue(complete.contains(Achievement.FIND_SUN_SIGIL));
        assertTrue(complete.contains(Achievement.FIND_MOON_SIGIL));
        assertTrue(complete.contains(Achievement.COMPLETE_ARTIFACT_SET));
        assertTrue(tracker.isUnlocked(Achievement.FIND_ANCIENT_RELIC));
        assertTrue(tracker.unlockArtifactMilestones(java.util.Map.of(
                ItemType.ANCIENT_RELIC, 1, ItemType.SUN_SIGIL, 1, ItemType.MOON_SIGIL, 1)).isEmpty());
    }

}
