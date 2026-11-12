package com.blockworld.core;

/** Deterministic ore distribution rules, separated from rendering/world state for testing. */
public final class OreRules {
    private OreRules() { }

    public static BlockType selectRelease100(int y, float roll) {
        if (!Float.isFinite(roll) || roll < 0f || roll >= 1f) {
            throw new IllegalArgumentException("roll must be in [0,1)");
        }
        if (y <= -10 && roll < 0.012f) return BlockType.EMERALD_ORE;
        if (y <= -9 && roll < 0.035f) return BlockType.GOLD_ORE;
        if (y <= -6 && roll < 0.085f) return BlockType.IRON_ORE;
        if (y <= -3 && roll < 0.145f) return BlockType.COPPER_ORE;
        if (roll < 0.22f) return BlockType.COAL_ORE;
        return BlockType.STONE;
    }
}
