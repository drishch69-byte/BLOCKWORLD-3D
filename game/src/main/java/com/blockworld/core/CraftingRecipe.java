package com.blockworld.core;

import java.util.Map;

/** Small deterministic recipe registry for the first survival crafting release. */
public record CraftingRecipe(int number, String name, Map<BlockType, Integer> blockCosts,
                             Map<ItemType, Integer> itemCosts, ItemType result, int resultCount) {
    public CraftingRecipe {
        if (number < 1 || resultCount < 1) throw new IllegalArgumentException("Некорректный рецепт");
        blockCosts = Map.copyOf(blockCosts);
        itemCosts = Map.copyOf(itemCosts);
    }

    public static java.util.List<CraftingRecipe> starterRecipes() {
        return java.util.List.of(
                new CraftingRecipe(1, "Доски", Map.of(BlockType.WOOD, 2), Map.of(), ItemType.PLANKS, 4),
                new CraftingRecipe(2, "Палки", Map.of(), Map.of(ItemType.PLANKS, 2), ItemType.STICKS, 4),
                new CraftingRecipe(3, "Верстак", Map.of(), Map.of(ItemType.PLANKS, 4), ItemType.CRAFTING_TABLE, 1),
                new CraftingRecipe(4, "Факелы", Map.of(BlockType.COAL_ORE, 1), Map.of(ItemType.STICKS, 2), ItemType.TORCH, 4),
                new CraftingRecipe(5, "Деревянная кирка", Map.of(), Map.of(ItemType.PLANKS, 3, ItemType.STICKS, 2), ItemType.WOODEN_PICKAXE, 1),
                new CraftingRecipe(6, "Каменная кирка", Map.of(BlockType.STONE, 3), Map.of(ItemType.STICKS, 2), ItemType.STONE_PICKAXE, 1),
                new CraftingRecipe(7, "Железная кирка", Map.of(BlockType.IRON_ORE, 3), Map.of(ItemType.STICKS, 2), ItemType.IRON_PICKAXE, 1),
                new CraftingRecipe(8, "Деревянная лопата", Map.of(), Map.of(ItemType.PLANKS, 1, ItemType.STICKS, 2), ItemType.WOODEN_SHOVEL, 1),
                new CraftingRecipe(9, "Железный шлем", Map.of(BlockType.IRON_ORE, 5), Map.of(), ItemType.IRON_HELMET, 1),
                new CraftingRecipe(10, "Железный нагрудник", Map.of(BlockType.IRON_ORE, 8), Map.of(), ItemType.IRON_CHESTPLATE, 1),
                new CraftingRecipe(11, "Железные поножи", Map.of(BlockType.IRON_ORE, 7), Map.of(), ItemType.IRON_LEGGINGS, 1),
                new CraftingRecipe(12, "Железные ботинки", Map.of(BlockType.IRON_ORE, 4), Map.of(), ItemType.IRON_BOOTS, 1)
        );
    }


    /** Extended recipes for 0.9.5. Older profiles keep their original recipe list. */
    public static java.util.List<CraftingRecipe> advancedRecipes() {
        java.util.List<CraftingRecipe> base = new java.util.ArrayList<>(starterRecipes());
        base.set(6, new CraftingRecipe(7, "Железная кирка", Map.of(),
                Map.of(ItemType.IRON_INGOT, 3, ItemType.STICKS, 2), ItemType.IRON_PICKAXE, 1));
        base.set(8, new CraftingRecipe(9, "Железный шлем", Map.of(), Map.of(ItemType.IRON_INGOT, 5), ItemType.IRON_HELMET, 1));
        base.set(9, new CraftingRecipe(10, "Железный нагрудник", Map.of(), Map.of(ItemType.IRON_INGOT, 8), ItemType.IRON_CHESTPLATE, 1));
        base.set(10, new CraftingRecipe(11, "Железные поножи", Map.of(), Map.of(ItemType.IRON_INGOT, 7), ItemType.IRON_LEGGINGS, 1));
        base.set(11, new CraftingRecipe(12, "Железные ботинки", Map.of(), Map.of(ItemType.IRON_INGOT, 4), ItemType.IRON_BOOTS, 1));
        base.add(new CraftingRecipe(13, "Деревянная мотыга", Map.of(),
                Map.of(ItemType.PLANKS, 2, ItemType.STICKS, 2), ItemType.WOODEN_HOE, 1));
        base.add(new CraftingRecipe(14, "Печь", Map.of(BlockType.STONE, 8),
                Map.of(), ItemType.FURNACE, 1));
        base.add(new CraftingRecipe(15, "Хлеб", Map.of(),
                Map.of(ItemType.WHEAT, 3), ItemType.BREAD, 1));
        return java.util.List.copyOf(base);
    }



    /** Complete 1.0.0 recipe catalogue. New recipes are appended to keep old indices stable. */
    public static java.util.List<CraftingRecipe> release100Recipes() {
        java.util.List<CraftingRecipe> recipes = new java.util.ArrayList<>(advancedRecipes());
        recipes.add(new CraftingRecipe(16, "Удочка", Map.of(),
                Map.of(ItemType.PLANKS, 2, ItemType.STICKS, 3), ItemType.FISHING_ROD, 1));
        return java.util.List.copyOf(recipes);
    }

    /** Farm collection tools for the 1.9.12 survival profile. */
    public static java.util.List<CraftingRecipe> farmProductRecipes() {
        java.util.List<CraftingRecipe> recipes = new java.util.ArrayList<>(release100Recipes());
        recipes.add(new CraftingRecipe(17, "Пустое ведро", Map.of(),
                Map.of(ItemType.IRON_INGOT, 3), ItemType.EMPTY_BUCKET, 1));
        recipes.add(new CraftingRecipe(18, "Ножницы", Map.of(),
                Map.of(ItemType.IRON_INGOT, 2), ItemType.SHEARS, 1));
        return java.util.List.copyOf(recipes);
    }

    /** Recipes for fencing and animal feeders, added without renumbering earlier recipes. */
    public static java.util.List<CraftingRecipe> farmBuildingRecipes() {
        java.util.List<CraftingRecipe> recipes = new java.util.ArrayList<>(farmProductRecipes());
        recipes.add(new CraftingRecipe(19, "Деревянное ограждение", Map.of(),
                Map.of(ItemType.PLANKS, 6, ItemType.STICKS, 2), ItemType.WOODEN_FENCE, 4));
        recipes.add(new CraftingRecipe(20, "Кормушка", Map.of(),
                Map.of(ItemType.PLANKS, 5, ItemType.STICKS, 2), ItemType.FEED_TROUGH, 1));
        return java.util.List.copyOf(recipes);
    }

    public boolean canCraft(Map<BlockType, Integer> blocks, Map<ItemType, Integer> items) {
        return blockCosts.entrySet().stream().allMatch(e -> blocks.getOrDefault(e.getKey(), 0) >= e.getValue())
                && itemCosts.entrySet().stream().allMatch(e -> items.getOrDefault(e.getKey(), 0) >= e.getValue());
    }

    public boolean craft(Map<BlockType, Integer> blocks, Map<ItemType, Integer> items) {
        if (!canCraft(blocks, items)) return false;
        blockCosts.forEach((type, count) -> blocks.put(type, blocks.get(type) - count));
        itemCosts.forEach((type, count) -> items.put(type, items.get(type) - count));
        items.merge(result, resultCount, Integer::sum);
        return true;
    }

    public String ingredientsText() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        blockCosts.forEach((type, count) -> parts.add(type.displayName() + " × " + count));
        itemCosts.forEach((type, count) -> parts.add(type.displayName() + " × " + count));
        return parts.isEmpty() ? "без ингредиентов" : String.join(" + ", parts);
    }
}
