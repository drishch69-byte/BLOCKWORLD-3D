package com.blockworld.net;

import com.blockworld.core.BlockPos;
import com.blockworld.core.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerChestStoreTest {
    @Test void savesIndependentSharedChestsIncludingEmptyOnes(@TempDir Path directory) throws Exception {
        MultiplayerChestStore store = new MultiplayerChestStore(directory.resolve("world.bwserver"));
        BlockPos full = new BlockPos(2, 4, 8);
        BlockPos empty = new BlockPos(-3, 5, 1);
        EnumMap<ItemType, Integer> contents = new EnumMap<>(ItemType.class);
        contents.put(ItemType.BREAD, 6);
        store.save(Map.of(full, contents, empty, Map.of()));

        Map<BlockPos, Map<ItemType, Integer>> loaded = store.load();
        assertEquals(6, loaded.get(full).get(ItemType.BREAD));
        assertTrue(loaded.containsKey(empty));
        assertTrue(loaded.get(empty).isEmpty());
    }
}
