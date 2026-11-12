package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

class FurnaceRecipeTest {
    @Test void ironSmeltingConsumesOreAndFuelAndCreatesIngot() {
        EnumMap<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        EnumMap<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        blocks.put(BlockType.IRON_ORE, 2);
        blocks.put(BlockType.COAL_ORE, 1);
        assertTrue(FurnaceRecipe.IRON_INGOT.canSmelt(blocks));
        assertTrue(FurnaceRecipe.IRON_INGOT.smelt(blocks, items));
        assertEquals(1, blocks.get(BlockType.IRON_ORE));
        assertEquals(0, blocks.get(BlockType.COAL_ORE));
        assertEquals(1, items.get(ItemType.IRON_INGOT));
        assertFalse(FurnaceRecipe.IRON_INGOT.smelt(blocks, items));
    }

    @Test void copperAndFishRecipesRequireFuelAndProduceTheirItems() {
        EnumMap<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        EnumMap<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        blocks.put(BlockType.COPPER_ORE, 1);
        blocks.put(BlockType.COAL_ORE, 2);
        items.put(ItemType.RAW_FISH, 1);
        assertTrue(FurnaceRecipe.COPPER_INGOT.smelt(blocks, items));
        assertTrue(FurnaceRecipe.COOKED_FISH.smelt(blocks, items));
        assertEquals(1, items.get(ItemType.COPPER_INGOT));
        assertEquals(1, items.get(ItemType.COOKED_FISH));
        assertEquals(0, blocks.get(BlockType.COAL_ORE));
    }
}
