package com.blockworld.core;

/** Timing rules for holding the mine action on a targeted voxel block. */
public final class MiningRules {
    private MiningRules() {
    }

    /**
     * Returns the amount of focused mining time required for a block.
     * A tool that is suited to the block type reduces the duration.
     */
    public static float durationSeconds(BlockType block, ToolType tool) {
        if (block == null) return 0.5f;
        float baseDuration = switch (block) {
            case LEAVES, WHEAT_CROP, CAVE_FERN -> 0.25f;
            case GRASS, DIRT, SAND, SNOW, FARMLAND, WATER, LAVA -> 0.45f;
            case WOOD, PODZOL, MUD, CAVE_MOSS -> 0.85f;
            case STONE, SANDSTONE, FURNACE, TREASURE_CHEST, CRYSTAL_CACHE, ANCIENT_CACHE -> 1.25f;
            case WOODEN_FENCE, FEED_TROUGH -> 0.85f;
            case COAL_ORE, IRON_ORE, GOLD_ORE, COPPER_ORE, EMERALD_ORE, AMETHYST_ORE,
                    RUBY_ORE, SAPPHIRE_ORE, GLOWING_CRYSTAL -> 2.0f;
        };
        if (tool != null && tool.givesBonus(block)) baseDuration *= 0.55f;
        return Math.max(0.12f, baseDuration);
    }
}
