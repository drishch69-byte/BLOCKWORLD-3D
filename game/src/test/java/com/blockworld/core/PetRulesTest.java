package com.blockworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetRulesTest {
    @Test
    void onlyFoxesCanBeTamedAndTheyNeedCookedFish() {
        assertTrue(PetRules.isTameable(CreatureType.FOX));
        assertEquals(ItemType.COOKED_FISH, PetRules.tamingFood(CreatureType.FOX));
        assertFalse(PetRules.isTameable(CreatureType.COW));
        assertNull(PetRules.tamingFood(CreatureType.COW));
    }

    @Test
    void dogsAreDefaultCompanionsAndFoxesBecomeCompanionsAfterTaming() {
        assertTrue(PetRules.isCompanion(CreatureType.DOG, false));
        assertTrue(PetRules.isCompanion(CreatureType.DOG, true));
        assertFalse(PetRules.isCompanion(CreatureType.FOX, false));
        assertTrue(PetRules.isCompanion(CreatureType.FOX, true));
        assertFalse(PetRules.isCompanion(CreatureType.COW, true));
    }

    @Test
    void persistenceKeepsFoxTamingButOnlyNonDefaultDogCommands() {
        assertTrue(PetRules.shouldPersistMode(CreatureType.FOX, PetMode.FOLLOW));
        assertTrue(PetRules.shouldPersistMode(CreatureType.FOX, PetMode.STAY));
        assertTrue(PetRules.shouldPersistMode(CreatureType.DOG, PetMode.STAY));
        assertFalse(PetRules.shouldPersistMode(CreatureType.DOG, PetMode.FOLLOW));
        assertFalse(PetRules.shouldPersistMode(CreatureType.COW, PetMode.FOLLOW));
    }
}
