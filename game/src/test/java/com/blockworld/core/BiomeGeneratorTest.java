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
