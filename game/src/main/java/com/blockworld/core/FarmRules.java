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

    /** Rain and nearby farmers provide gentle crop-growth bonuses. */
    public static float advance(float currentSeconds, float elapsedSeconds,
                                boolean raining, boolean farmerNearby) {
        return advance(currentSeconds, elapsedSeconds, raining, farmerNearby, false);
    }

    /** Nearby farm animals provide a small additional growth bonus in the 1.9.11 profile. */
    public static float advance(float currentSeconds, float elapsedSeconds,
                                boolean raining, boolean farmerNearby, boolean farmAnimalNearby) {
        float multiplier = (raining ? 1.25f : 1f) * (farmerNearby ? 1.15f : 1f)
                * (farmAnimalNearby ? 1.10f : 1f);
        return advance(currentSeconds, elapsedSeconds * multiplier);
    }

    public static boolean isMature(float progressSeconds) {
        return Float.isFinite(progressSeconds) && progressSeconds >= MATURATION_SECONDS;
    }

    public static int harvestSeeds(int cropCount) {
        return Math.max(1, Math.min(3, cropCount));
    }
}
