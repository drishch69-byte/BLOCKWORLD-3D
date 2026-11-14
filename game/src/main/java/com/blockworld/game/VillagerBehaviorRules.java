package com.blockworld.game;

import com.blockworld.core.CreatureType;

/** Small, deterministic rules for villager daily routines and danger awareness. */
final class VillagerBehaviorRules {
    static final float FLEE_RADIUS = 9f;
    static final float FLEE_SPEED_MULTIPLIER = 1.8f;

    private static final float NIGHT_START = 0.58f;
    private static final float DAWN_END = 0.08f;

    private VillagerBehaviorRules() {
    }

    static boolean shouldRest(CreatureType type, float dayPhase) {
        if (type == null || !type.isVillager() || type == CreatureType.VILLAGER_GUARD
                || !Float.isFinite(dayPhase)) return false;
        float phase = dayPhase - (float) Math.floor(dayPhase);
        return phase >= NIGHT_START || phase < DAWN_END;
    }

    static boolean shouldFlee(CreatureType villager, CreatureType hostile,
                              float horizontalDistanceSquared, float radius) {
        if (villager == null || hostile == null || !villager.isVillager()
                || villager == CreatureType.VILLAGER_GUARD || !hostile.isHostile()
                || !Float.isFinite(horizontalDistanceSquared) || horizontalDistanceSquared < 0f
                || !Float.isFinite(radius) || radius < 0f) return false;
        return horizontalDistanceSquared <= radius * radius;
    }
}
