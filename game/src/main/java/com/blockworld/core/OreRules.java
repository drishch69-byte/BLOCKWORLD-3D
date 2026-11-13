package com.blockworld.core;

/** Deterministic ore distribution rules, separated from rendering/world state for testing. */
public final class OreRules {
    private OreRules() { }

    /** New rare gem bands for version 1.9.5; all previous roll ranges stay intact. */
    public static BlockType selectDeepCaves195(int y, float roll) {
        if (!Float.isFinite(roll) || roll < 0f || roll >= 1f) {
            throw new IllegalArgumentException("roll must be in [0,1)");
        }
        if (y <= -10 && roll >= 0.15f && roll < 0.18f) return BlockType.RUBY_ORE;
        if (y <= -7 && roll >= 0.22f && roll < 0.26f) return BlockType.SAPPHIRE_ORE;
        return selectRelease100(y, roll);
    }

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
