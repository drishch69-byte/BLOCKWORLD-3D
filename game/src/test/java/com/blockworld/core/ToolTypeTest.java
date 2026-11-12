package com.blockworld.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolTypeTest {
    @Test void pickaxeTiersGateStoneIronAndGoldOre() {
        assertFalse(ToolType.canMine(BlockType.STONE, null));
        assertTrue(ToolType.canMine(BlockType.STONE, ToolType.WOODEN_PICKAXE));
        assertFalse(ToolType.canMine(BlockType.IRON_ORE, ToolType.WOODEN_PICKAXE));
        assertTrue(ToolType.canMine(BlockType.IRON_ORE, ToolType.STONE_PICKAXE));
        assertFalse(ToolType.canMine(BlockType.GOLD_ORE, ToolType.STONE_PICKAXE));
        assertTrue(ToolType.canMine(BlockType.GOLD_ORE, ToolType.IRON_PICKAXE));
    }

    @Test void shovelGivesExtraDropsForSoftBlocksOnly() {
        assertEquals(1, ToolType.bonusDrops(BlockType.DIRT, ToolType.WOODEN_SHOVEL));
        assertEquals(0, ToolType.bonusDrops(BlockType.STONE, ToolType.WOODEN_SHOVEL));
        assertEquals(1, ToolType.bonusDrops(BlockType.FARMLAND, ToolType.WOODEN_HOE));
    }
}
