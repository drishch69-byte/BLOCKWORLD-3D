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
                Map.of(7L, 45f, 8L, 120f));
        AnimalDataStore.save(world, state);
        AnimalDataStore.State loaded = AnimalDataStore.load(world);
        assertEquals(state, loaded);
        assertTrue(Files.exists(AnimalDataStore.sidecar(world)));
        assertFalse(Files.exists(world));
    }

    @Test
    void missingOrMalformedAnimalSidecarIsSafe() throws Exception {
        Path world = temp.resolve("OldFarm.bwworld");
        assertEquals(AnimalDataStore.State.empty(), AnimalDataStore.load(world));
        Files.writeString(AnimalDataStore.sidecar(world),
                "baby.-1=4\nbaby.bad=9\nbaby.7=NaN\nbaby.8=45\n"
                        + "cooldown.3=-1\ncooldown.9=Infinity\ncooldown.10=12\n"
                        + "production.2=-4\nproduction.bad=5\nproduction.9=Infinity\n"
                        + "production.10=120\n");
        AnimalDataStore.State loaded = AnimalDataStore.load(world);
        assertEquals(Map.of(8L, 45f), loaded.babyAges());
        assertEquals(Map.of(10L, 12f), loaded.cooldowns());
        assertEquals(Map.of(10L, 120f), loaded.productionCooldowns());
    }
}
