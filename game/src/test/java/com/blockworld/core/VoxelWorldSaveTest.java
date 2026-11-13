package com.blockworld.core;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.AssetManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void version181SpawnsAndPersistsRabbitsOnNaturalHabitats() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion version = VersionManager.find("1.8.1");
        VoxelWorld source = new VoxelWorld(assets);
        source.generate(version, 8675309L);

        long rabbits = source.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.RABBIT).count();
        assertTrue(rabbits >= 3, "New wildlife profile should generate several rabbits");
        for (WorldCreature rabbit : source.getCreatures()) {
            if (rabbit.type() != CreatureType.RABBIT) continue;
            int x = (int) Math.floor(rabbit.x());
            int z = (int) Math.floor(rabbit.z());
            int surfaceY = source.highestBlockY(x, z);
            BlockType surface = source.getBlock(new BlockPos(x, surfaceY, z));
            BiomeType biome = source.getBiomeAt(x, z);
            assertTrue((surface == BlockType.GRASS
                            && (biome == BiomeType.PLAINS || biome == BiomeType.FOREST))
                    || (surface == BlockType.PODZOL && biome == BiomeType.TAIGA)
                    || (surface == BlockType.SNOW && biome == BiomeType.SNOWY_TUNDRA),
                    "Rabbit should spawn in a suitable natural habitat");
        }

        Path file = tempDir.resolve("version-181-rabbits.bwworld");
        source.save(file, version);
        assertTrue(Files.readString(file).contains(",rabbit,"));

        VoxelWorld restored = new VoxelWorld(assets);
        assertEquals(version.id(), restored.load(file));
        assertEquals(rabbits, restored.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.RABBIT).count());
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
    void version110GeneratesCrystalLakesNewBiomesWildlifeAndCompanionAndSavesThem() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion version = VersionManager.find("1.1.0");
        VoxelWorld source = new VoxelWorld(assets);
        source.generate(version, 8675309L);

        boolean taiga = false;
        boolean swamp = false;
        for (int x = -112; x <= 112; x += 4) {
            for (int z = -112; z <= 112; z += 4) {
                taiga |= source.getBiomeAt(x, z) == BiomeType.TAIGA;
                swamp |= source.getBiomeAt(x, z) == BiomeType.SWAMP;
            }
        }
        assertTrue(taiga, "New profile should generate taiga");
        assertTrue(swamp, "New profile should generate swamps");

        int crystals = 0;
        int undergroundWater = 0;
        for (int x = -112; x <= 112; x++) {
            for (int z = -112; z <= 112; z++) {
                for (int y = -12; y < 0; y++) {
                    BlockType block = source.getBlock(new BlockPos(x, y, z));
                    if (block == BlockType.AMETHYST_ORE) crystals++;
                    if (block == BlockType.WATER) undergroundWater++;
                }
            }
        }
        assertTrue(crystals > 0, "Expected crystal veins in underground geodes");
        assertTrue(undergroundWater > 0, "Expected underground lake water");

        assertTrue(source.getCreatures().stream().anyMatch(c -> c.type() == CreatureType.FOX));
        assertTrue(source.getCreatures().stream().anyMatch(c -> c.type() == CreatureType.TURTLE));
        assertTrue(source.getCreatures().stream().anyMatch(c -> c.type() == CreatureType.DOG));

        Path file = tempDir.resolve("version-110.bwworld");
        source.save(file, version);
        String saved = Files.readString(file);
        assertTrue(saved.contains("extendedBiomes=true"));
        assertTrue(saved.contains(",amethyst_ore"));
        assertTrue(saved.contains(",fox,"));
        assertTrue(saved.contains(",turtle,"));
        assertTrue(saved.contains(",dog,"));

        VoxelWorld restored = new VoxelWorld(assets);
        restored.load(file);
        assertEquals(source.getCreatureCount(), restored.getCreatureCount());
        assertEquals(source.getBlockCount(), restored.getBlockCount());
        assertEquals(source.getBiomeAt(24, 16), restored.getBiomeAt(24, 16));
        assertEquals(source.getCreatures().stream().filter(c -> c.type() == CreatureType.DOG).count(),
                restored.getCreatures().stream().filter(c -> c.type() == CreatureType.DOG).count());
    }

    @Test
    void failedLoadClearsBiomeFlagsAndMetadataFromTheRejectedFile() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        Path file = tempDir.resolve("broken-biome-world.bwworld");
        Files.writeString(file,
                "BLOCKWORLD_WORLD\nformat=2\ngameVersion=1.1.0\nseed=999\n" +
                "biomes=true\nextendedBiomes=true\n--blocks--\n" +
                "0,0,0,grass\nthis-is-not-a-valid-block-record\n");

        VoxelWorld world = new VoxelWorld(assets);
        assertThrows(IOException.class, () -> world.load(file));
        assertEquals(0, world.getBlockCount());
        assertEquals(20261112L, world.getSeed());
        assertEquals(0, world.getWorldRadius());
        assertFalse(world.hasExtendedBiomes());
        assertTrue(world.getLandmarks().isEmpty());
        assertEquals(BiomeType.PLAINS, world.getBiomeAt(0, 0));
    }

    @Test
    void version120CreatesExplorationSitesAndLootChestsAndRestoresMapMarkers() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion version = VersionManager.find("1.2.0");
        VoxelWorld source = new VoxelWorld(assets);
        source.generate(version, 246813579L);

        assertEquals(128, source.getWorldRadius());
        int riverSurface = source.highestBlockY(0, 0);
        assertEquals(BlockType.WATER, source.getBlock(new BlockPos(0, riverSurface, 0)),
                "River generation must replace the solid surface block with water");
        assertEquals(6, source.getLandmarks().size());
        assertEquals(4L, source.countBlocks(BlockType.TREASURE_CHEST));
        assertTrue(source.getBlockCount() > 200_000);
        assertTrue(source.hasExtendedBiomes());

        Path file = tempDir.resolve("version-120.bwworld");
        source.save(file, version);
        String saved = Files.readString(file);
        assertTrue(saved.contains(",treasure_chest"));

        VoxelWorld restored = new VoxelWorld(assets);
        assertEquals(version.id(), restored.load(file));
        assertEquals(source.getBlockCount(), restored.getBlockCount());
        assertEquals(4L, restored.countBlocks(BlockType.TREASURE_CHEST));
        assertEquals(source.getLandmarks(), restored.getLandmarks());
        assertTrue(restored.hasExtendedBiomes());
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
