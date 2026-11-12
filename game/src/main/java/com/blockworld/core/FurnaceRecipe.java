package com.blockworld.core;

import java.util.Map;

/** Deterministic smelting catalogue. Fuel remains the coal-ore block in this preview. */
public enum FurnaceRecipe {
    IRON_INGOT("Железный слиток", BlockType.IRON_ORE, null, ItemType.IRON_INGOT),
    COPPER_INGOT("Медный слиток", BlockType.COPPER_ORE, null, ItemType.COPPER_INGOT),
    COOKED_FISH("Жареная рыба", null, ItemType.RAW_FISH, ItemType.COOKED_FISH);

    private final String displayName;
    private final BlockType blockInput;
    private final ItemType itemInput;
    private final ItemType output;

    FurnaceRecipe(String displayName, BlockType blockInput, ItemType itemInput, ItemType output) {
        this.displayName = displayName;
        this.blockInput = blockInput;
        this.itemInput = itemInput;
        this.output = output;
    }

    public String displayName() { return displayName; }
    public ItemType output() { return output; }
    public int outputCount() { return 1; }
    public boolean canSmelt(Map<BlockType, Integer> blocks) {
        return blockInput != null && blocks.getOrDefault(blockInput, 0) >= 1
                && blocks.getOrDefault(BlockType.COAL_ORE, 0) >= 1;
    }
    public boolean canSmelt(Map<BlockType, Integer> blocks, Map<ItemType, Integer> items) {
        boolean hasInput = blockInput != null ? blocks.getOrDefault(blockInput, 0) >= 1
                : items.getOrDefault(itemInput, 0) >= 1;
        return hasInput && blocks.getOrDefault(BlockType.COAL_ORE, 0) >= 1;
    }
    public boolean smelt(Map<BlockType, Integer> blocks, Map<ItemType, Integer> items) {
        if (!canSmelt(blocks, items)) return false;
        if (blockInput != null) blocks.put(blockInput, blocks.get(blockInput) - 1);
        else items.put(itemInput, items.get(itemInput) - 1);
        blocks.put(BlockType.COAL_ORE, blocks.get(BlockType.COAL_ORE) - 1);
        items.merge(output, outputCount(), Integer::sum);
        return true;
    }
    public static java.util.List<FurnaceRecipe> all() { return java.util.List.of(values()); }
}
