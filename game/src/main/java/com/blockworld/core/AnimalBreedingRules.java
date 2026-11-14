package com.blockworld.core;

/** Deterministic breeding, feeding, and growth rules for farm animals. */
public final class AnimalBreedingRules {
    public static final float GROWTH_SECONDS = 180f;
    public static final float BREEDING_COOLDOWN_SECONDS = 300f;
    public static final float PARTNER_DISTANCE = 5.5f;
    private AnimalBreedingRules() { }

    public static boolean isBreedableAdult(CreatureType type) {
        return type == CreatureType.COW || type == CreatureType.SHEEP
                || type == CreatureType.PIG || type == CreatureType.CHICKEN;
    }

    public static boolean isBaby(CreatureType type) {
        return type == CreatureType.CALF || type == CreatureType.LAMB
                || type == CreatureType.PIGLET || type == CreatureType.CHICK;
    }

    public static boolean isFarmAnimal(CreatureType type) {
        return isBreedableAdult(type) || isBaby(type);
    }

    public static CreatureType babyTypeFor(CreatureType adult) {
        return switch (adult) {
            case COW -> CreatureType.CALF;
            case SHEEP -> CreatureType.LAMB;
            case PIG -> CreatureType.PIGLET;
            case CHICKEN -> CreatureType.CHICK;
            default -> throw new IllegalArgumentException("Это животное не размножается: " + adult);
        };
    }

    public static CreatureType adultTypeFor(CreatureType baby) {
        return switch (baby) {
            case CALF -> CreatureType.COW;
            case LAMB -> CreatureType.SHEEP;
            case PIGLET -> CreatureType.PIG;
            case CHICK -> CreatureType.CHICKEN;
            default -> throw new IllegalArgumentException("Это не детёныш: " + baby);
        };
    }

    public static CreatureType textureTypeFor(CreatureType type) {
        return isBaby(type) ? adultTypeFor(type) : type;
    }

    public static ItemType feedFor(CreatureType animal) {
        return switch (animal) {
            case COW, SHEEP, PIG -> ItemType.WHEAT;
            case CHICKEN -> ItemType.WHEAT_SEEDS;
            default -> null;
        };
    }

    public static boolean canBreedTogether(CreatureType first, CreatureType second) {
        return isBreedableAdult(first) && first == second;
    }

    public static float advanceGrowth(float currentSeconds, float elapsedSeconds) {
        if (!Float.isFinite(currentSeconds) || currentSeconds < 0f) currentSeconds = 0f;
        if (!Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0f) {
            return Math.min(GROWTH_SECONDS, currentSeconds);
        }
        return Math.min(GROWTH_SECONDS, currentSeconds + elapsedSeconds);
    }

    public static boolean isMature(float elapsedSeconds) {
        return Float.isFinite(elapsedSeconds) && elapsedSeconds >= GROWTH_SECONDS;
    }

    public static float advanceCooldown(float remainingSeconds, float elapsedSeconds) {
        if (!Float.isFinite(remainingSeconds) || remainingSeconds < 0f) remainingSeconds = 0f;
        if (!Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0f) return remainingSeconds;
        return Math.max(0f, remainingSeconds - elapsedSeconds);
    }
}
