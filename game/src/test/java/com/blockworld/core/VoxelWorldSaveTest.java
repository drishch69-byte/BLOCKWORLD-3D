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
    void persistsCustomWorldRadiusAndKeepsLegacyRadiusDefaults() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion version = VersionManager.find("0.3.0");
        VoxelWorld source = new VoxelWorld(assets);
        source.generate(version, 987654321L, 192);
        assertEquals(192, source.getWorldRadius());

        Path file = tempDir.resolve("custom-radius.bwworld");
        source.save(file, version);
        assertTrue(Files.readString(file).contains("worldRadius=192"));

        VoxelWorld restored = new VoxelWorld(assets);
        assertEquals(version.id(), restored.load(file));
        assertEquals(192, restored.getWorldRadius());
        assertEquals(source.getBlockCount(), restored.getBlockCount());

        Path legacy = tempDir.resolve("legacy-no-radius.bwworld");
        Files.writeString(legacy, "BLOCKWORLD_WORLD\nformat=2\ngameVersion=0.3.0\nseed=7\n--blocks--\n0,0,0,grass\n");
        VoxelWorld legacyWorld = new VoxelWorld(assets);
        legacyWorld.load(legacy);
        assertEquals(version.worldRadius(), legacyWorld.getWorldRadius());
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
    @Test
    void version195AddsGemVeinsAndCrystalCavernsAndRoundTripsThem() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion previous = VersionManager.find("1.9.4");
        GameVersion updated = VersionManager.find("1.9.5");

        VoxelWorld oldWorld = new VoxelWorld(assets);
        oldWorld.generate(previous, 8675309L);
        assertEquals(0L, oldWorld.countBlocks(BlockType.RUBY_ORE));
        assertEquals(0L, oldWorld.countBlocks(BlockType.SAPPHIRE_ORE));

        VoxelWorld world = new VoxelWorld(assets);
        world.generate(updated, 8675309L);
        assertTrue(world.countBlocks(BlockType.RUBY_ORE) > 0, "Expected ruby deposits");
        assertTrue(world.countBlocks(BlockType.SAPPHIRE_ORE) > 0, "Expected sapphire deposits");
        assertTrue(world.countBlocks(BlockType.AMETHYST_ORE) > 0, "Expected amethyst crystal walls");
        assertTrue(world.getBlockCount() < oldWorld.getBlockCount(),
                "Expanded cave rooms should carve additional underground space");

        Path file = tempDir.resolve("version-195-crystal-caverns.bwworld");
        world.save(file, updated);
        String saved = Files.readString(file);
        assertTrue(saved.contains(",ruby_ore"));
        assertTrue(saved.contains(",sapphire_ore"));
        assertTrue(saved.contains("format=2"));

        VoxelWorld restored = new VoxelWorld(assets);
        assertEquals(updated.id(), restored.load(file));
        assertEquals(world.getBlockCount(), restored.getBlockCount());
        assertEquals(world.countBlocks(BlockType.RUBY_ORE), restored.countBlocks(BlockType.RUBY_ORE));
        assertEquals(world.countBlocks(BlockType.SAPPHIRE_ORE), restored.countBlocks(BlockType.SAPPHIRE_ORE));
    }

    @Test
    void version196AddsCaveBiomesGlowfloraAndUndergroundLakesAndRoundTripsThem() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion previous = VersionManager.find("1.9.5");
        GameVersion updated = VersionManager.find("1.9.6");

        VoxelWorld oldWorld = new VoxelWorld(assets);
        oldWorld.generate(previous, 8675309L);
        assertFalse(oldWorld.hasCaveBiomes());
        assertEquals(0L, oldWorld.countBlocks(BlockType.GLOWING_CRYSTAL));
        assertEquals(0L, oldWorld.countBlocks(BlockType.CAVE_MOSS));
        assertEquals(0L, oldWorld.countBlocks(BlockType.CAVE_FERN));

        VoxelWorld world = new VoxelWorld(assets);
        world.generate(updated, 8675309L);
        assertTrue(world.hasCaveBiomes());
        assertTrue(world.countBlocks(BlockType.GLOWING_CRYSTAL) > 0, "Expected luminous crystal blocks");
        assertTrue(world.countBlocks(BlockType.CAVE_MOSS) > 0, "Expected moss patches");
        assertTrue(world.countBlocks(BlockType.CAVE_FERN) > 0, "Expected cave ferns");
        assertTrue(world.countBlocks(BlockType.WATER) > oldWorld.countBlocks(BlockType.WATER),
                "Underground lake rooms should add water pools");
        assertTrue(world.getCaveBiomeAt(24, -8, 24) != CaveBiomeType.NONE
                        || world.getCaveBiomeAt(-24, -8, 24) != CaveBiomeType.NONE,
                "Expected a classified underground biome at a generated cell");

        Path file = tempDir.resolve("version-196-cave-biomes.bwworld");
        world.save(file, updated);
        String saved = Files.readString(file);
        assertTrue(saved.contains("caveBiomes=true"));
        assertTrue(saved.contains(",glowing_crystal"));
        assertTrue(saved.contains(",cave_moss"));
        assertTrue(saved.contains(",cave_fern"));
        assertTrue(saved.contains("format=2"));

        VoxelWorld restored = new VoxelWorld(assets);
        assertEquals(updated.id(), restored.load(file));
        assertTrue(restored.hasCaveBiomes());
        assertEquals(world.getBlockCount(), restored.getBlockCount());
        assertEquals(world.countBlocks(BlockType.GLOWING_CRYSTAL), restored.countBlocks(BlockType.GLOWING_CRYSTAL));
        assertEquals(world.countBlocks(BlockType.CAVE_MOSS), restored.countBlocks(BlockType.CAVE_MOSS));
        assertEquals(world.countBlocks(BlockType.CAVE_FERN), restored.countBlocks(BlockType.CAVE_FERN));

        Path olderSave = tempDir.resolve("version-195-no-cave-biomes.bwworld");
        oldWorld.save(olderSave, previous);
        VoxelWorld oldRestored = new VoxelWorld(assets);
        oldRestored.load(olderSave);
        assertFalse(oldRestored.hasCaveBiomes());
    }

    @Test
    void version197AddsCrystalTreesCachesAndAHostileSentinelAndRoundTripsThem() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion previous = VersionManager.find("1.9.6");
        GameVersion updated = VersionManager.find("1.9.7");

        VoxelWorld oldWorld = new VoxelWorld(assets);
        oldWorld.generate(previous, 8675309L);
        assertEquals(0L, oldWorld.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.CRYSTAL_SENTINEL).count());
        assertEquals(0L, oldWorld.countBlocks(BlockType.CRYSTAL_CACHE));

        VoxelWorld world = new VoxelWorld(assets);
        world.generate(updated, 8675309L);
        long sentinels = world.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.CRYSTAL_SENTINEL).count();
        assertEquals(1L, sentinels, "A new 1.9.7 world should have one cave boss");
        assertTrue(world.countBlocks(BlockType.CRYSTAL_CACHE) > 0, "Expected crystal caches");
        assertTrue(world.countBlocks(BlockType.GLOWING_CRYSTAL) > oldWorld.countBlocks(BlockType.GLOWING_CRYSTAL),
                "Crystal trees should add glowing trunks and branches");
        assertTrue(world.countBlocks(BlockType.CAVE_FERN) > oldWorld.countBlocks(BlockType.CAVE_FERN),
                "Crystal trees should add fern-like crowns");

        WorldCreature boss = world.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.CRYSTAL_SENTINEL)
                .findFirst().orElseThrow();
        int bossX = (int) Math.floor(boss.x());
        int bossZ = (int) Math.floor(boss.z());
        assertEquals((int) Math.floor(boss.y() - 1f),
                world.highestBlockYAtOrBelow(bossX, bossZ, (int) Math.floor(boss.y() - 0.2f)));

        Path file = tempDir.resolve("version-197-crystal-guardians.bwworld");
        world.save(file, updated);
        String saved = Files.readString(file);
        assertTrue(saved.contains(",crystal_cache"));
        assertTrue(saved.contains("format=2"));

        VoxelWorld restored = new VoxelWorld(assets);
        assertEquals(updated.id(), restored.load(file));
        assertEquals(world.getBlockCount(), restored.getBlockCount());
        assertEquals(world.countBlocks(BlockType.CRYSTAL_CACHE), restored.countBlocks(BlockType.CRYSTAL_CACHE));
        assertTrue(restored.getCreatures().stream()
                .anyMatch(creature -> creature.type() == CreatureType.CRYSTAL_SENTINEL));
    }

    @Test
    void version198AddsTwoAncientTemplesAndPersistsTempleWardensAndCaches() throws IOException {
        AssetManager assets = new DesktopAssetManager(true);
        GameVersion previous = VersionManager.find("1.9.7");
        GameVersion updated = VersionManager.find("1.9.8");

        VoxelWorld oldWorld = new VoxelWorld(assets);
        oldWorld.generate(previous, 8675309L);
        assertEquals(0L, oldWorld.countBlocks(BlockType.ANCIENT_CACHE));
        assertEquals(0L, oldWorld.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.TEMPLE_WARDEN).count());

        VoxelWorld world = new VoxelWorld(assets);
        world.generate(updated, 8675309L);
        assertEquals(2L, world.countBlocks(BlockType.ANCIENT_CACHE));
        assertEquals(2L, world.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.TEMPLE_WARDEN).count());
        for (WorldCreature guardian : world.getCreatures()) {
            if (guardian.type() != CreatureType.TEMPLE_WARDEN) continue;
            int x = (int) Math.floor(guardian.x());
            int z = (int) Math.floor(guardian.z());
            int floorY = world.highestBlockY(x, z);
            assertEquals(BlockType.SANDSTONE, world.getBlock(new BlockPos(x, floorY, z)));
            assertEquals(floorY + 1f, guardian.y());
        }

        Path file = tempDir.resolve("version-198-ancient-temples.bwworld");
        world.save(file, updated);
        String saved = Files.readString(file);
        assertTrue(saved.contains(",ancient_cache"));
        assertTrue(saved.contains(",temple_warden,"));
        assertTrue(saved.contains("format=2"));

        VoxelWorld restored = new VoxelWorld(assets);
        assertEquals(updated.id(), restored.load(file));
        assertEquals(2L, restored.countBlocks(BlockType.ANCIENT_CACHE));
        assertEquals(2L, restored.getCreatures().stream()
                .filter(creature -> creature.type() == CreatureType.TEMPLE_WARDEN).count());
        assertEquals(world.getBlockCount(), restored.getBlockCount());
    }

}
