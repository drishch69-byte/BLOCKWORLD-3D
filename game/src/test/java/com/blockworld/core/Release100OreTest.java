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
    void invalidNoiseRollIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OreRules.selectRelease100(-10, 1f));
    }
}
