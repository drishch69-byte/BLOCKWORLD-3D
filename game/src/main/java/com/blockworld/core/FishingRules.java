package com.blockworld.core;

/** Small deterministic ruleset for the peaceful fishing activity. */
public final class FishingRules {
    public static final float CATCH_CHANCE = 0.72f;
    public static final float CAST_RANGE = 6.0f;
    public static final float CAST_COOLDOWN_SECONDS = 2.5f;

    private FishingRules() { }

    public static boolean catchesFish(float roll) {
        if (!Float.isFinite(roll) || roll < 0f || roll >= 1f) {
            throw new IllegalArgumentException("roll должен находиться в диапазоне [0, 1)");
        }
        return roll < CATCH_CHANCE;
    }

    public static float advanceCooldown(float remaining, float elapsed) {
        if (!Float.isFinite(remaining) || remaining < 0f) remaining = 0f;
        if (!Float.isFinite(elapsed) || elapsed <= 0f) return remaining;
        return Math.max(0f, remaining - elapsed);
    }
}
