package com.blockworld.net;

import com.blockworld.core.ArmorSlot;
import com.blockworld.core.BlockType;
import com.blockworld.core.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerPlayerStoreTest {
    @Test void playerProfilesAreSavedSeparatelyAndRestored(@TempDir Path directory) throws Exception {
        MultiplayerPlayerStore store = new MultiplayerPlayerStore(directory.resolve("world.bwserver"));
        EnumMap<BlockType, Integer> aliceBlocks = new EnumMap<>(BlockType.class);
        EnumMap<ItemType, Integer> aliceItems = new EnumMap<>(ItemType.class);
        aliceBlocks.put(BlockType.WOOD, 23);
        aliceItems.put(ItemType.PLANKS, 9);
        var alice = new MultiplayerPlayerStore.Snapshot(aliceBlocks, aliceItems,
                EnumSet.of(ArmorSlot.HELMET));

        EnumMap<BlockType, Integer> bobBlocks = new EnumMap<>(BlockType.class);
        EnumMap<ItemType, Integer> bobItems = new EnumMap<>(ItemType.class);
        bobBlocks.put(BlockType.STONE, 4);
        bobItems.put(ItemType.BREAD, 2);
        var bob = new MultiplayerPlayerStore.Snapshot(bobBlocks, bobItems, EnumSet.noneOf(ArmorSlot.class));

        store.save("Alice_1", alice);
        store.save("Bob-2", bob);

        var loadedAlice = store.load("Alice_1").orElseThrow();
        var loadedBob = store.load("Bob-2").orElseThrow();
        assertEquals(23, loadedAlice.blocks().get(BlockType.WOOD));
        assertEquals(9, loadedAlice.items().get(ItemType.PLANKS));
        assertTrue(loadedAlice.armor().contains(ArmorSlot.HELMET));
        assertEquals(4, loadedBob.blocks().get(BlockType.STONE));
        assertEquals(2, loadedBob.items().get(ItemType.BREAD));
        assertEquals(0, loadedBob.items().get(ItemType.PLANKS));
        assertTrue(store.load("unknown").isEmpty());
    }

    @Test void invalidStacksAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MultiplayerPlayerStore.Snapshot(
                java.util.Map.of(BlockType.WOOD, -1), java.util.Map.of(), java.util.Set.of()));
    }
}
