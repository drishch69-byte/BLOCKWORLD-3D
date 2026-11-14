package com.blockworld.core;

import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CraftingRecipeTest {
    @Test
    void planksRecipeConsumesWoodAndProducesFourPlanks() {
        Map<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        blocks.put(BlockType.WOOD, 2);
        assertTrue(CraftingRecipe.starterRecipes().get(0).craft(blocks, items));
        assertEquals(0, blocks.get(BlockType.WOOD));
        assertEquals(4, items.get(ItemType.PLANKS));
    }

    @Test
    void recipeDoesNotConsumeAnythingWhenIngredientsAreMissing() {
        Map<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        blocks.put(BlockType.WOOD, 1);
        assertFalse(CraftingRecipe.starterRecipes().get(0).craft(blocks, items));
        assertEquals(1, blocks.get(BlockType.WOOD));
        assertTrue(items.isEmpty());
    }

    @Test
    void recipeChainCreatesSticksWorkbenchAndTorches() {
        Map<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        blocks.put(BlockType.WOOD, 4);
        blocks.put(BlockType.COAL_ORE, 1);
        var recipes = CraftingRecipe.starterRecipes();
        assertTrue(recipes.get(0).craft(blocks, items));
        assertTrue(recipes.get(0).craft(blocks, items));
        assertTrue(recipes.get(1).craft(blocks, items));
        assertTrue(recipes.get(2).craft(blocks, items));
        assertTrue(recipes.get(3).craft(blocks, items));
        assertEquals(2, items.get(ItemType.STICKS));
        assertEquals(1, items.get(ItemType.CRAFTING_TABLE));
        assertEquals(4, items.get(ItemType.TORCH));
    }
    @Test
    void includesToolsAndFourArmorCraftingRecipes() {
        var recipes = CraftingRecipe.starterRecipes();
        assertEquals(12, recipes.size());
        assertEquals(ItemType.WOODEN_PICKAXE, recipes.get(4).result());
        assertEquals(ItemType.STONE_PICKAXE, recipes.get(5).result());
        assertEquals(ItemType.IRON_PICKAXE, recipes.get(6).result());
        assertEquals(ItemType.WOODEN_SHOVEL, recipes.get(7).result());
        assertEquals(ItemType.IRON_HELMET, recipes.get(8).result());
        assertEquals(ItemType.IRON_CHESTPLATE, recipes.get(9).result());
        assertEquals(ItemType.IRON_LEGGINGS, recipes.get(10).result());
        assertEquals(ItemType.IRON_BOOTS, recipes.get(11).result());
    }

    @Test
    void advancedRecipesAddFarmingAndFurnaceAndUseSmeltedIron() {
        var recipes = CraftingRecipe.advancedRecipes();
        assertEquals(15, recipes.size());
        assertEquals(ItemType.IRON_PICKAXE, recipes.get(6).result());
        assertEquals(3, recipes.get(6).itemCosts().get(ItemType.IRON_INGOT));
        assertEquals(ItemType.WOODEN_HOE, recipes.get(12).result());
        assertEquals(ItemType.FURNACE, recipes.get(13).result());
        assertEquals(ItemType.BREAD, recipes.get(14).result());
    }


    @Test void releaseCatalogAppendsFishingRodWithoutChangingEarlierEntries() {
        var recipes = CraftingRecipe.release100Recipes();
        assertEquals(16, recipes.size());
        assertEquals(ItemType.FISHING_ROD, recipes.get(15).result());
    }

    @Test void farmBuildingCatalogAddsFenceAndAutomaticFeederRecipes() {
        var recipes = CraftingRecipe.farmBuildingRecipes();
        assertEquals(21, recipes.size());
        assertEquals(ItemType.WOODEN_FENCE, recipes.get(18).result());
        assertEquals(4, recipes.get(18).resultCount());
        assertEquals(ItemType.FEED_TROUGH, recipes.get(19).result());
        assertEquals(5, recipes.get(19).itemCosts().get(ItemType.PLANKS));
        assertEquals(2, recipes.get(19).itemCosts().get(ItemType.STICKS));
        assertEquals(ItemType.WOODEN_GATE, recipes.get(20).result());
        assertEquals(4, recipes.get(20).itemCosts().get(ItemType.PLANKS));
        assertEquals(2, recipes.get(20).itemCosts().get(ItemType.STICKS));
    }

    @Test void farmProductCatalogAddsBucketAndShearsRecipesAfterExistingRecipes() {
        var recipes = CraftingRecipe.farmProductRecipes();
        assertEquals(18, recipes.size());
        assertEquals(ItemType.EMPTY_BUCKET, recipes.get(16).result());
        assertEquals(3, recipes.get(16).itemCosts().get(ItemType.IRON_INGOT));
        assertEquals(ItemType.SHEARS, recipes.get(17).result());
        assertEquals(2, recipes.get(17).itemCosts().get(ItemType.IRON_INGOT));

        Map<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        items.put(ItemType.IRON_INGOT, 5);
        assertTrue(recipes.get(16).craft(blocks, items));
        assertTrue(recipes.get(17).craft(blocks, items));
        assertEquals(1, items.get(ItemType.EMPTY_BUCKET));
        assertEquals(1, items.get(ItemType.SHEARS));
        assertEquals(0, items.get(ItemType.IRON_INGOT));
    }
}
