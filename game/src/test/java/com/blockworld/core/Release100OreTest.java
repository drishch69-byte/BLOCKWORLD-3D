package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Release100OreTest {
    @Test
    void copperAndEmeraldHaveDeterministicDeepOreBands() {
        assertEquals(BlockType.EMERALD_ORE, OreRules.selectRelease100(-10, 0.005f));
        assertEquals(BlockType.COPPER_ORE, OreRules.selectRelease100(-4, 0.12f));
        assertEquals(BlockType.STONE, OreRules.selectRelease100(-4, 0.90f));
    }

    @Test
    void deepCaveProfileAddsRubyAndSapphireWithoutChangingLegacyOreRolls() {
        assertEquals(BlockType.RUBY_ORE, OreRules.selectDeepCaves195(-10, 0.16f));
        assertEquals(BlockType.SAPPHIRE_ORE, OreRules.selectDeepCaves195(-8, 0.24f));
        assertEquals(BlockType.EMERALD_ORE, OreRules.selectDeepCaves195(-10, 0.005f));
        assertEquals(BlockType.COAL_ORE, OreRules.selectDeepCaves195(-10, 0.19f));
        assertEquals(BlockType.STONE, OreRules.selectDeepCaves195(-6, 0.24f));
        assertEquals(OreRules.selectRelease100(-4, 0.12f), OreRules.selectDeepCaves195(-4, 0.12f));
    }

    @Test
    void deepCaveOreRejectsInvalidNoiseRoll() {
        assertThrows(IllegalArgumentException.class, () -> OreRules.selectDeepCaves195(-10, 1f));
        assertThrows(IllegalArgumentException.class, () -> OreRules.selectDeepCaves195(-10, -0.1f));
    }

    @Test
    void invalidNoiseRollIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OreRules.selectRelease100(-10, 1f));
    }
}
