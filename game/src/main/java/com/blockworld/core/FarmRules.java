package com.blockworld.core;

/** Shared crop growth and harvest rules. */
public final class FarmRules {
    public static final float MATURATION_SECONDS = 45f;
    private FarmRules() { }

    public static float advance(float currentSeconds, float elapsedSeconds) {
        if (!Float.isFinite(currentSeconds) || currentSeconds < 0f) currentSeconds = 0f;
        if (!Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0f) return Math.min(MATURATION_SECONDS, currentSeconds);
        return Math.min(MATURATION_SECONDS, currentSeconds + elapsedSeconds);
    }

    public static boolean isMature(float progressSeconds) {
        return Float.isFinite(progressSeconds) && progressSeconds >= MATURATION_SECONDS;
    }

    public static int harvestSeeds(int cropCount) {
        return Math.max(1, Math.min(3, cropCount));
    }
}
