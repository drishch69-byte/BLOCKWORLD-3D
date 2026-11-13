package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PetDataStoreTest {
    @TempDir Path tempDir;

    @Test
    void savesAndLoadsStayCommandsWithoutChangingWorldFile() throws Exception {
        Path world = tempDir.resolve("Companions.bwworld");
        Map<Long, PetMode> expected = Map.of(12L, PetMode.STAY, 27L, PetMode.STAY);
        PetDataStore.save(world, expected);
        Map<Long, PetMode> loaded = new HashMap<>();
        PetDataStore.load(world, loaded);
        assertEquals(expected, loaded);
        assertTrue(Files.exists(PetDataStore.sidecar(world)));
        assertFalse(Files.exists(world));
    }

    @Test
    void missingSidecarClearsOldStateAndPetModesToggle() throws Exception {
        Path world = tempDir.resolve("OldWorld.bwworld");
        Map<Long, PetMode> loaded = new HashMap<>();
        loaded.put(8L, PetMode.STAY);
        PetDataStore.load(world, loaded);
        assertTrue(loaded.isEmpty());
        assertEquals(PetMode.STAY, PetMode.FOLLOW.toggle());
        assertEquals(PetMode.FOLLOW, PetMode.STAY.toggle());
    }

    @Test
    void ignoresMalformedPetEntriesAndUnknownModes() throws Exception {
        Path world = tempDir.resolve("BrokenEntries.bwworld");
        Files.writeString(PetDataStore.sidecar(world),
                "pet.-1=stay\npet.not-a-number=stay\npet.4=unknown\npet.9=stay\n");
        Map<Long, PetMode> loaded = new HashMap<>();
        PetDataStore.load(world, loaded);
        assertEquals(Map.of(9L, PetMode.STAY), loaded);
    }
}
