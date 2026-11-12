package com.blockworld.core;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.AssetManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelWorldSaveTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsBlocksAndSeed() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion version = VersionManager.find("0.3.0");
        VoxelWorld source = new VoxelWorld(assets);
        source.generate(version, 123456L);

        Path file = tempDir.resolve("test.bwworld");
        int originalCount = source.getBlockCount();
        source.save(file, version);
        String generatedData = Files.readString(file);
        assertTrue(generatedData.contains(",coal_ore"));
        assertTrue(generatedData.contains(",iron_ore"));
        assertTrue(generatedData.contains(",gold_ore"));

        VoxelWorld restored = new VoxelWorld(assets);
        String savedVersion = restored.load(file);
        assertEquals(version.id(), savedVersion);
        assertEquals(originalCount, restored.getBlockCount());
        assertEquals(123456L, restored.getSeed());
    }

    @Test
    void creaturesUpdateGeneratesAndPersistsCreaturesWithoutBreakingBlockSaves() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion version = VersionManager.find("0.8.0");
        VoxelWorld source = new VoxelWorld(assets);
        source.generate(version, 8675309L);

        assertTrue(source.getCreatureCount() >= 10);
        assertTrue(source.getCreatures().stream().anyMatch(c -> c.type() == CreatureType.VILLAGER));
        assertTrue(source.getBlockCount() > 100_000);

        Path file = tempDir.resolve("creatures.bwworld");
        source.save(file, version);
        String saved = Files.readString(file);
        assertTrue(saved.contains("format=2"));
        assertTrue(saved.contains("--creatures--"));

        VoxelWorld restored = new VoxelWorld(assets);
        restored.load(file);
        assertEquals(source.getBlockCount(), restored.getBlockCount());
        assertEquals(source.getCreatureCount(), restored.getCreatureCount());
        assertEquals(source.getCreatures().get(0), restored.getCreatures().get(0));
    }

    @Test
    void loadsLegacyFormatOneWorldWithoutCreatures() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        Path file = tempDir.resolve("legacy-format-one.bwworld");
        Files.writeString(file, "BLOCKWORLD_WORLD\nformat=1\ngameVersion=0.7.5\nseed=19\n--blocks--\n0,0,0,grass\n");

        VoxelWorld restored = new VoxelWorld(assets);
        restored.load(file);
        assertEquals(1, restored.getBlockCount());
        assertEquals(0, restored.getCreatureCount());
        assertEquals(BiomeType.PLAINS, restored.getBiomeAt(0, 0));

        // Saving an old flat world from a newer profile must not relabel its terrain as biome-generated.
        Path upgraded = tempDir.resolve("upgraded.bwworld");
        restored.save(upgraded, VersionManager.find("0.9.5"));
        String upgradedText = Files.readString(upgraded);
        assertTrue(upgradedText.contains("format=2"));
        assertTrue(upgradedText.contains("biomes=false"));
        VoxelWorld reopened = new VoxelWorld(assets);
        reopened.load(upgraded);
        assertEquals(BiomeType.PLAINS, reopened.getBiomeAt(0, 0));
    }

    @Test
    void preservesUnknownBlockIdsForNewerBuilds() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion version = VersionManager.find("0.3.0");
        Path file = tempDir.resolve("future.bwworld");
        Files.writeString(file,
                "BLOCKWORLD_WORLD\nformat=1\ngameVersion=9.0.0\nseed=88\n--blocks--\n" +
                "0,0,0,grass\n1,0,0,crystal_block\n");

        VoxelWorld olderBuild = new VoxelWorld(assets);
        olderBuild.load(file);
        Path resaved = tempDir.resolve("resaved.bwworld");
        olderBuild.save(resaved, version);

        assertTrue(Files.readString(resaved).contains("1,0,0,crystal_block"));
    }
}
