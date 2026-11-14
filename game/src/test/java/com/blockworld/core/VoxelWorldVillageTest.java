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

        WorldCreature librarian = world.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.VILLAGER_LIBRARIAN)
                .findFirst().orElseThrow();
        BlockPos library = world.snapshotBlocks().entrySet().stream()
                .filter(entry -> entry.getValue() == BlockType.BOOKSHELF)
                .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        float dx = librarian.x() - (library.x() + 0.5f);
        float dz = librarian.z() - (library.z() + 0.5f);
        assertTrue(dx * dx + dz * dz <= 4f * 4f,
                "The librarian should start close to the generated village library");
        assertEquals(library.y(), Math.round(librarian.y()),
                "The librarian should spawn at the house floor height, not on the roof");
    }

    @Test
    void olderWorkplaceProfileDoesNotGainNewHouseBlocks() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        world.generate(VersionManager.find("1.9.25"), 20261114L, 64);

        assertEquals(0L, world.snapshotBlocks().values().stream()
                .filter(type -> type == BlockType.BOOKSHELF || type == BlockType.VILLAGER_BED).count());
    }
}
