package com.blockworld.core;

import com.jme3.asset.DesktopAssetManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelWorldVillageTest {
    @Test
    void newestProfileGeneratesLibraryFurnaceAndResidentBedsInsideVillageHomes() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        world.generate(VersionManager.find("1.9.26"), 20261114L, 64);

        long bookshelves = world.snapshotBlocks().values().stream()
                .filter(type -> type == BlockType.BOOKSHELF).count();
        long beds = world.snapshotBlocks().values().stream()
                .filter(type -> type == BlockType.VILLAGER_BED).count();
        long furnaces = world.snapshotBlocks().values().stream()
                .filter(type -> type == BlockType.FURNACE).count();

        assertEquals(2L, bookshelves);
        assertEquals(4L, beds);
        assertTrue(furnaces >= 2L);
    }

    @Test
    void olderWorkplaceProfileDoesNotGainNewHouseBlocks() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        world.generate(VersionManager.find("1.9.25"), 20261114L, 64);

        assertEquals(0L, world.snapshotBlocks().values().stream()
                .filter(type -> type == BlockType.BOOKSHELF || type == BlockType.VILLAGER_BED).count());
    }
}
