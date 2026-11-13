package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningRulesTest {
    @Test
    void harderBlocksTakeLongerToMine() {
        assertTrue(MiningRules.durationSeconds(BlockType.STONE, null)
                > MiningRules.durationSeconds(BlockType.DIRT, null));
        assertTrue(MiningRules.durationSeconds(BlockType.GOLD_ORE, null)
                > MiningRules.durationSeconds(BlockType.STONE, null));
    }

    @Test
    void suitableToolReducesMiningTime() {
        float withoutTool = MiningRules.durationSeconds(BlockType.DIRT, null);
        float withShovel = MiningRules.durationSeconds(BlockType.DIRT, ToolType.WOODEN_SHOVEL);
        assertTrue(withShovel < withoutTool);
        assertEquals(withoutTool * 0.55f, withShovel, 0.0001f);
    }

    @Test
    void minimumDurationStaysPositive() {
        assertTrue(MiningRules.durationSeconds(BlockType.LEAVES, ToolType.WOODEN_HOE) >= 0.12f);
        assertEquals(0.5f, MiningRules.durationSeconds(null, null));
    }
}
