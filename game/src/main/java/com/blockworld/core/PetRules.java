package com.blockworld.core;

/** Rules for tameable wildlife and persistent companion behaviour. */
public final class PetRules {
    private PetRules() { }

    public static boolean isTameable(CreatureType type) {
        return type == CreatureType.FOX;
    }

    public static ItemType tamingFood(CreatureType type) {
        return isTameable(type) ? ItemType.COOKED_FISH : null;
    }

    public static boolean isCompanion(CreatureType type, boolean tamed) {
        return isCompanion(type, tamed, true);
    }

    public static boolean isCompanion(CreatureType type, boolean tamed, boolean foxCompanionsEnabled) {
        return type == CreatureType.DOG || (type == CreatureType.FOX && tamed && foxCompanionsEnabled);
    }

    /** Dogs only need a saved entry when stopped; fox entries also record that they are tamed. */
    public static boolean shouldPersistMode(CreatureType type, PetMode mode) {
        return mode != null && (type == CreatureType.FOX
                || (type == CreatureType.DOG && mode == PetMode.STAY));
    }
}
