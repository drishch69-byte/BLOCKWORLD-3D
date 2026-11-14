package com.blockworld.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalBreedingRulesTest {
    @Test
    void eachFarmAnimalHasTheRightBabyAndFeed() {
        assertEquals(CreatureType.CALF, AnimalBreedingRules.babyTypeFor(CreatureType.COW));
        assertEquals(CreatureType.LAMB, AnimalBreedingRules.babyTypeFor(CreatureType.SHEEP));
        assertEquals(CreatureType.PIGLET, AnimalBreedingRules.babyTypeFor(CreatureType.PIG));
        assertEquals(CreatureType.CHICK, AnimalBreedingRules.babyTypeFor(CreatureType.CHICKEN));
        assertEquals(ItemType.WHEAT, AnimalBreedingRules.feedFor(CreatureType.COW));
        assertEquals(ItemType.WHEAT, AnimalBreedingRules.feedFor(CreatureType.SHEEP));
        assertEquals(ItemType.WHEAT, AnimalBreedingRules.feedFor(CreatureType.PIG));
        assertEquals(ItemType.WHEAT_SEEDS, AnimalBreedingRules.feedFor(CreatureType.CHICKEN));
        assertNull(AnimalBreedingRules.feedFor(CreatureType.FOX));
    }

    @Test
    void onlyAdultsOfTheSameSpeciesCanBreed() {
        assertTrue(AnimalBreedingRules.canBreedTogether(CreatureType.COW, CreatureType.COW));
        assertFalse(AnimalBreedingRules.canBreedTogether(CreatureType.COW, CreatureType.SHEEP));
        assertFalse(AnimalBreedingRules.canBreedTogether(CreatureType.CALF, CreatureType.COW));
        assertTrue(AnimalBreedingRules.isFarmAnimal(CreatureType.PIGLET));
        assertFalse(AnimalBreedingRules.isBreedableAdult(CreatureType.PIGLET));
        assertEquals(CreatureType.CHICKEN, AnimalBreedingRules.adultTypeFor(CreatureType.CHICK));
    }

    @Test
    void babiesGrowAndCooldownsReachZeroWithoutGoingNegative() {
        assertEquals(60f, AnimalBreedingRules.advanceGrowth(30f, 30f));
        assertEquals(AnimalBreedingRules.GROWTH_SECONDS,
                AnimalBreedingRules.advanceGrowth(179f, 10f));
        assertTrue(AnimalBreedingRules.isMature(AnimalBreedingRules.GROWTH_SECONDS));
        assertEquals(0f, AnimalBreedingRules.advanceCooldown(2f, 3f));
        assertEquals(4f, AnimalBreedingRules.advanceCooldown(4f, -3f));
    }
}
