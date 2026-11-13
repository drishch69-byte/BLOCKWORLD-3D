package com.blockworld.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class CaveBiomeGeneratorTest {
    @Test
    void caveBiomeSamplingIsDeterministicAndRequiresUndergroundDepth() {
        assertEquals(CaveBiomeGenerator.biomeAt(8675309L, 24, -8, 24),
                CaveBiomeGenerator.biomeAt(8675309L, 24, -8, 24));
        assertEquals(CaveBiomeType.NONE, CaveBiomeGenerator.biomeAt(8675309L, 24, 0, 24));
    }

    @Test
    void broadSeededAreaContainsAllThreeCaveBiomes() {
        EnumSet<CaveBiomeType> found = EnumSet.noneOf(CaveBiomeType.class);
        for (long seed = 1; seed <= 8; seed++) {
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    found.add(CaveBiomeGenerator.biomeAt(seed, x * 48 + 24, -8, z * 48 + 24));
                }
            }
        }
        assertTrue(found.contains(CaveBiomeType.CRYSTAL_GROTTO));
        assertTrue(found.contains(CaveBiomeType.MOSS_CAVERN));
        assertTrue(found.contains(CaveBiomeType.UNDERGROUND_LAKE));
        assertTrue(found.contains(CaveBiomeType.NONE));
    }

    @Test
    void caveBiomesAreOnlyEnabledInTheNewProfile() {
        assertFalse(CaveBiomeGenerator.supports("1.9.5"));
        assertTrue(CaveBiomeGenerator.supports("1.9.6"));
        assertTrue(CaveBiomeGenerator.supports("2.0.0"));
        assertFalse(CaveBiomeGenerator.supports("not-a-version"));
        assertFalse(CaveBiomeGenerator.supports(null));
    }
}
