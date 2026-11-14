package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AnimalDataStoreTest {
    @TempDir Path temp;

    @Test
    void savesBabyAgeAndBothParentCooldownsToSeparateSidecar() throws Exception {
        Path world = temp.resolve("Farm.bwworld");
        AnimalDataStore.State state = new AnimalDataStore.State(
                Map.of(40L, 72.5f), Map.of(10L, 225f, 11L, 225f),
                Map.of(7L, 45f, 8L, 120f, 9L, 0f),
                Map.of(new BlockPos(-4, 2, 9), new AnimalFeederRules.Stock(12, 3, 20f)));
        AnimalDataStore.save(world, state);
        AnimalDataStore.State loaded = AnimalDataStore.load(world);
        assertEquals(state, loaded);
        assertTrue(Files.exists(AnimalDataStore.sidecar(world)));
        assertFalse(Files.exists(world));
        assertEquals(new AnimalFeederRules.Stock(12, 3, 20f),
                loaded.feeders().get(new BlockPos(-4, 2, 9)));
    }

    @Test
    void missingOrMalformedAnimalSidecarIsSafe() throws Exception {
        Path world = temp.resolve("OldFarm.bwworld");
        assertEquals(AnimalDataStore.State.empty(), AnimalDataStore.load(world));
        Files.writeString(AnimalDataStore.sidecar(world),
                "baby.-1=4\nbaby.bad=9\nbaby.7=NaN\nbaby.8=45\n"
                        + "cooldown.3=-1\ncooldown.9=Infinity\ncooldown.10=12\n"
                        + "production.2=-4\nproduction.bad=5\nproduction.9=Infinity\n"
                        + "production.10=120\nproduction.11=0\n"
                        + "feeder:4,2,-3=8,5,30\nfeeder:1,1,1=99,0,30\n"
                        + "feeder:0,0,0=bad,2,10\n");
        AnimalDataStore.State loaded = AnimalDataStore.load(world);
        assertEquals(Map.of(8L, 45f), loaded.babyAges());
        assertEquals(Map.of(10L, 12f), loaded.cooldowns());
        assertEquals(Map.of(10L, 120f, 11L, 0f), loaded.productionCooldowns());
        assertEquals(Map.of(new BlockPos(4, 2, -3),
                new AnimalFeederRules.Stock(8, 5, 30f)), loaded.feeders());
    }
}
