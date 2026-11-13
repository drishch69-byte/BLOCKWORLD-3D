package com.blockworld.net;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.WorldCreature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerWorldStoreTest {
    @Test void savesAndLoadsBlocksAndCreaturesAtomically(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("server-world.bwserver");
        MultiplayerWorldStore store = new MultiplayerWorldStore(file);
        Map<BlockPos, BlockType> blocks = Map.of(
                new BlockPos(-2, 4, 8), BlockType.AMETHYST_ORE,
                new BlockPos(3, 5, -1), BlockType.WOOD);
        List<WorldCreature> creatures = List.of(
                new WorldCreature(3, CreatureType.DOG, 1.5f, 2f, 3.5f, 0.7f),
                new WorldCreature(4, CreatureType.TURTLE, -4f, 2f, 9f, 1.1f));

        store.save(123456L, true, true, true, 64, blocks, creatures);
        MultiplayerWorldStore.Snapshot loaded = store.load().orElseThrow();

        assertEquals(123456L, loaded.seed());
        assertTrue(loaded.activeBiomes());
        assertTrue(loaded.extendedBiomes());
        assertTrue(loaded.enchantedBiomes());
        assertEquals(64, loaded.radius());
        assertEquals(blocks, loaded.blocks());
        assertEquals(creatures, loaded.creatures());
        assertFalse(java.nio.file.Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
    }

    @Test void reportsMissingSnapshotWithoutCreatingFile(@TempDir Path directory) throws Exception {
        MultiplayerWorldStore store = new MultiplayerWorldStore(directory.resolve("missing.bwserver"));
        assertTrue(store.load().isEmpty());
        assertFalse(java.nio.file.Files.exists(store.file()));
    }

    @Test void loadsLegacyFormatOneWithoutEnchantedBiomeFlag(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("legacy-world.bwserver");
        java.nio.file.Files.writeString(file, String.join("\n",
                "BLOCKWORLD_SERVER_WORLD", "format=1", "seed=77", "activeBiomes=true",
                "extendedBiomes=true", "radius=16", "--blocks--", "B 0 1 0 GRASS",
                "--creatures--", ""));
        MultiplayerWorldStore.Snapshot loaded = new MultiplayerWorldStore(file).load().orElseThrow();
        assertEquals(77L, loaded.seed());
        assertTrue(loaded.activeBiomes());
        assertTrue(loaded.extendedBiomes());
        assertFalse(loaded.enchantedBiomes());
        assertEquals(BlockType.GRASS, loaded.blocks().get(new BlockPos(0, 1, 0)));
    }

}
