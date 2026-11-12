package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class FarmDataStoreTest {
    @TempDir Path temp;

    @Test void cropProgressPersistsInSeparateSidecar() throws Exception {
        Path world = temp.resolve("farm.world");
        HashMap<BlockPos, Float> source = new HashMap<>();
        source.put(new BlockPos(-3, 4, 15), 22.5f);
        FarmDataStore.save(world, source);
        HashMap<BlockPos, Float> loaded = new HashMap<>();
        FarmDataStore.load(world, loaded);
        assertEquals(22.5f, loaded.get(new BlockPos(-3, 4, 15)), 0.001f);
        assertTrue(Files.exists(FarmDataStore.sidecar(world)));
    }
}
