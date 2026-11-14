package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VillageEconomyDataStoreTest {
    @Test
    void savesAndLoadsStocksAndPartialWorkCycles(@TempDir Path directory) throws Exception {
        Path world = directory.resolve("market.bwworld");
        VillageEconomy economy = new VillageEconomy();
        economy.setStock(CreatureType.VILLAGER_LIBRARIAN, ItemType.TORCH, 9);
        economy.advanceWork(Map.of(CreatureType.VILLAGER_FARMER, 1), 29f);
        VillageEconomyDataStore.save(world, economy);

        VillageEconomy restored = new VillageEconomy();
        VillageEconomyDataStore.load(world, restored);

        assertEquals(9, restored.stock(CreatureType.VILLAGER_LIBRARIAN, ItemType.TORCH));
        assertEquals(29f, restored.workProgress(CreatureType.VILLAGER_FARMER));
        assertTrue(java.nio.file.Files.exists(VillageEconomyDataStore.sidecar(world)));
    }

    @Test
    void leavesDefaultsWhenNoSidecarExists(@TempDir Path directory) throws Exception {
        VillageEconomy economy = new VillageEconomy();
        VillageEconomyDataStore.load(directory.resolve("fresh.bwworld"), economy);
        assertEquals(8, economy.stock(CreatureType.VILLAGER_FARMER, ItemType.EMERALD));
    }
}
