package com.blockworld.core;

import java.util.Set;

/** Utility tools used only for mining and gathering in the voxel world. */
public enum ToolType {
    WOODEN_PICKAXE(ItemType.WOODEN_PICKAXE, 1, Set.of(BlockType.STONE, BlockType.COAL_ORE)),
    STONE_PICKAXE(ItemType.STONE_PICKAXE, 2, Set.of(BlockType.IRON_ORE, BlockType.COPPER_ORE)),
    IRON_PICKAXE(ItemType.IRON_PICKAXE, 3, Set.of(BlockType.GOLD_ORE, BlockType.EMERALD_ORE,
            BlockType.RUBY_ORE, BlockType.SAPPHIRE_ORE)),
    WOODEN_SHOVEL(ItemType.WOODEN_SHOVEL, 0, Set.of(BlockType.GRASS, BlockType.DIRT, BlockType.SAND, BlockType.SNOW)),
    WOODEN_HOE(ItemType.WOODEN_HOE, 0, Set.of(BlockType.FARMLAND));

    private final ItemType item;
    private final int miningTier;
    private final Set<BlockType> bonusBlocks;

    ToolType(ItemType item, int miningTier, Set<BlockType> bonusBlocks) {
        this.item = item;
        this.miningTier = miningTier;
        this.bonusBlocks = Set.copyOf(bonusBlocks);
    }

    public ItemType item() { return item; }
    public int miningTier() { return miningTier; }
    public boolean givesBonus(BlockType block) { return bonusBlocks.contains(block); }

    public static ToolType fromItem(ItemType item) {
        for (ToolType tool : values()) if (tool.item == item) return tool;
        return null;
    }

    public static int requiredTier(BlockType block) {
        return switch (block) {
            case STONE, COAL_ORE -> 1;
            case IRON_ORE, COPPER_ORE -> 2;
            case GOLD_ORE, EMERALD_ORE, RUBY_ORE, SAPPHIRE_ORE -> 3;
            default -> 0;
        };
    }

    public static boolean canMine(BlockType block, ToolType selected) {
        int required = requiredTier(block);
        return required == 0 || (selected != null && selected.miningTier >= required);
    }

    public static int bonusDrops(BlockType block, ToolType selected) {
        return selected != null && selected.givesBonus(block) ? 1 : 0;
    }
}
