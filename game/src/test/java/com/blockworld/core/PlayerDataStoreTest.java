package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDataStoreTest {
    @TempDir Path temp;

    @Test
    void savesAndLoadsBlockAndCraftedItemInventoryWithoutChangingWorldFile() throws Exception {
        Path world = temp.resolve("test.bwworld");
        Map<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        blocks.put(BlockType.WOOD, 7);
        items.put(ItemType.PLANKS, 12);
        var equipped = java.util.EnumSet.of(ArmorSlot.HELMET, ArmorSlot.BOOTS);
        PlayerDataStore.save(world, blocks, items, equipped);

        Map<BlockType, Integer> loadedBlocks = new EnumMap<>(BlockType.class);
        Map<ItemType, Integer> loadedItems = new EnumMap<>(ItemType.class);
        var loadedArmor = java.util.EnumSet.noneOf(ArmorSlot.class);
        PlayerDataStore.load(world, loadedBlocks, loadedItems, loadedArmor);
        assertEquals(7, loadedBlocks.get(BlockType.WOOD));
        assertEquals(12, loadedItems.get(ItemType.PLANKS));
        assertEquals(equipped, loadedArmor);
        assertTrue(java.nio.file.Files.exists(PlayerDataStore.sidecar(world)));
        assertFalse(java.nio.file.Files.exists(world));
    }
    @Test
    void loadsInventorySidecarsCreatedBeforeArmorWasAdded() throws Exception {
        Path world = temp.resolve("old-world.bwworld");
        Path sidecar = PlayerDataStore.sidecar(world);
        java.nio.file.Files.writeString(sidecar, "block.dirt=5\nitem.planks=3\n");
        Map<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        var equipped = java.util.EnumSet.of(ArmorSlot.HELMET);
        PlayerDataStore.load(world, blocks, items, equipped);
        assertEquals(5, blocks.get(BlockType.DIRT));
        assertEquals(3, items.get(ItemType.PLANKS));
        assertTrue(equipped.isEmpty());
    }

}
