package com.blockworld.core;

/** Shared, bounded hunger restoration rules for food items. */
public final class FoodRules {
    public static final float MAX_HUNGER = 20f;
    public static final float BERRY_RESTORATION = 6f;
    public static final float BREAD_RESTORATION = 8f;
    public static final float COOKED_FISH_RESTORATION = 10f;

    private FoodRules() { }

    public static float restoreHunger(float current, float restoration) {
        float hunger = Float.isFinite(current) ? Math.max(0f, Math.min(MAX_HUNGER, current)) : 0f;
        if (!Float.isFinite(restoration) || restoration <= 0f) return hunger;
        return Math.min(MAX_HUNGER, hunger + restoration);
    }
}
