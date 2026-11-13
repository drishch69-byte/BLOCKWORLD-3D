package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class BiomeGeneratorTest {
    @Test void biomeSamplingIsDeterministic() {
        assertEquals(BiomeGenerator.biomeAt(12345L, -20, 31), BiomeGenerator.biomeAt(12345L, -20, 31));
        assertNotNull(BiomeGenerator.biomeAt(-77L, 88, -41));
    }

    @Test void broadAreaContainsMultipleBiomeTypes() {
        EnumSet<BiomeType> found = EnumSet.noneOf(BiomeType.class);
        for (int x = -180; x <= 180; x += 6) {
            for (int z = -180; z <= 180; z += 6) found.add(BiomeGenerator.biomeAt(20261113L, x, z));
        }
        assertTrue(found.size() >= 4, "Expected varied biomes, found: " + found);
    }

    @Test void expandedSamplerAddsTaigaAndSwampWithoutChangingLegacySampler() {
        EnumSet<BiomeType> found = EnumSet.noneOf(BiomeType.class);
        for (long seed = 1; seed <= 12; seed++) {
            for (int x = -240; x <= 240; x += 8) {
                for (int z = -240; z <= 240; z += 8) {
                    found.add(BiomeGenerator.biomeAtV110(seed, x, z));
                }
            }
        }
        assertTrue(found.contains(BiomeType.TAIGA), "Expected the new taiga biome");
        assertTrue(found.contains(BiomeType.SWAMP), "Expected the new swamp biome");
        BiomeType legacyBiome = BiomeGenerator.biomeAt(12345L, 0, 0);
        assertNotEquals(BiomeType.TAIGA, legacyBiome);
        assertNotEquals(BiomeType.SWAMP, legacyBiome);
    }

    @Test void cachedBiomeOverloadKeepsMountainHeightIdentical() {
        boolean testedMountain = false;
        for (long seed = 1; seed <= 5 && !testedMountain; seed++) {
            for (int x = -160; x <= 160 && !testedMountain; x += 4) {
                for (int z = -160; z <= 160; z += 4) {
                    BiomeType biome = BiomeGenerator.biomeAtV110(seed, x, z);
                    if (biome == BiomeType.MOUNTAINS) {
                        assertEquals(BiomeGenerator.mountainHeightBonus(seed, x, z),
                                BiomeGenerator.mountainHeightBonus(seed, x, z, biome));
                        testedMountain = true;
                        break;
                    }
                }
            }
        }
        assertTrue(testedMountain, "Expected a sampled mountain column");
    }

    @Test void mountainsAddHeightOnlyInMountainBiome() {
        boolean testedMountain = false;
        for (int x = -160; x <= 160 && !testedMountain; x += 4) {
            for (int z = -160; z <= 160; z += 4) {
                if (BiomeGenerator.biomeAt(555L, x, z) == BiomeType.MOUNTAINS) {
                    assertTrue(BiomeGenerator.mountainHeightBonus(555L, x, z) >= 2);
                    testedMountain = true;
                    break;
                }
            }
        }
        assertTrue(testedMountain, "Seed should expose at least one mountain area in the test region");
    }
}
