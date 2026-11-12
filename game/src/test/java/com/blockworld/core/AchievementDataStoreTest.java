package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AchievementDataStoreTest {
    @TempDir Path temp;

    @Test
    void saveLoadRoundTripKeepsUnlockedAchievementsAndBiomes() throws Exception {
        Path world = temp.resolve("test.bwworld");
        AchievementTracker source = new AchievementTracker();
        source.unlock(Achievement.FIRST_TRADE);
        source.discover(BiomeType.FOREST);
        source.discover(BiomeType.DESERT);
        AchievementDataStore.save(world, source);

        AchievementTracker restored = new AchievementTracker();
        AchievementDataStore.load(world, restored);
        assertTrue(restored.isUnlocked(Achievement.FIRST_TRADE));
        assertFalse(restored.isUnlocked(Achievement.FIRST_FISH));
        assertEquals(2, restored.discoveredBiomeCount());
    }
}
