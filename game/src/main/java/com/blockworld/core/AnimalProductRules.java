package com.blockworld.core;

/** Production drops and collection cooldowns for farm animals. */
public final class AnimalProductRules {
    public static final float EGG_INTERVAL_SECONDS = 75f;
    public static final float MILK_COOLDOWN_SECONDS = 90f;
    public static final float WOOL_REGROW_SECONDS = 180f;
    public static final float MAX_COOLDOWN_SECONDS = WOOL_REGROW_SECONDS;

    private AnimalProductRules() { }

    public static boolean produces(CreatureType animal) {
        return animal == CreatureType.COW || animal == CreatureType.SHEEP
                || animal == CreatureType.CHICKEN;
    }

    public static ItemType productFor(CreatureType animal) {
        return switch (animal) {
            case COW -> ItemType.MILK_BUCKET;
            case SHEEP -> ItemType.WOOL;
            case CHICKEN -> ItemType.EGG;
            default -> null;
        };
    }

    public static ItemType requiredToolFor(CreatureType animal) {
        return switch (animal) {
            case COW -> ItemType.EMPTY_BUCKET;
            case SHEEP -> ItemType.SHEARS;
            default -> null;
        };
    }

    public static float initialCooldown(CreatureType animal) {
        return animal == CreatureType.CHICKEN ? EGG_INTERVAL_SECONDS : 0f;
    }

    public static float cooldownAfterCollection(CreatureType animal) {
        return switch (animal) {
            case COW -> MILK_COOLDOWN_SECONDS;
            case SHEEP -> WOOL_REGROW_SECONDS;
            case CHICKEN -> EGG_INTERVAL_SECONDS;
            default -> 0f;
        };
    }

    public static int productCount(CreatureType animal) {
        return animal == CreatureType.SHEEP ? 2 : produces(animal) ? 1 : 0;
    }
}
