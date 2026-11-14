package com.blockworld.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalProductRulesTest {
    @Test
    void mapsFarmAnimalsToProductsAndCollectionTools() {
        assertEquals(ItemType.MILK_BUCKET, AnimalProductRules.productFor(CreatureType.COW));
        assertEquals(ItemType.EMPTY_BUCKET, AnimalProductRules.requiredToolFor(CreatureType.COW));
        assertEquals(ItemType.WOOL, AnimalProductRules.productFor(CreatureType.SHEEP));
        assertEquals(ItemType.SHEARS, AnimalProductRules.requiredToolFor(CreatureType.SHEEP));
        assertEquals(ItemType.EGG, AnimalProductRules.productFor(CreatureType.CHICKEN));
        assertNull(AnimalProductRules.requiredToolFor(CreatureType.CHICKEN));
        assertNull(AnimalProductRules.productFor(CreatureType.PIG));
        assertFalse(AnimalProductRules.produces(CreatureType.PIG));
    }

    @Test
    void productionCooldownsAndProductAmountsAreSpeciesSpecific() {
        assertEquals(1, AnimalProductRules.productCount(CreatureType.COW));
        assertEquals(2, AnimalProductRules.productCount(CreatureType.SHEEP));
        assertEquals(1, AnimalProductRules.productCount(CreatureType.CHICKEN));
        assertEquals(0, AnimalProductRules.productCount(CreatureType.PIG));
        assertEquals(75f, AnimalProductRules.initialCooldown(CreatureType.CHICKEN));
        assertEquals(0f, AnimalProductRules.initialCooldown(CreatureType.COW));
        assertEquals(90f, AnimalProductRules.cooldownAfterCollection(CreatureType.COW));
        assertEquals(180f, AnimalProductRules.cooldownAfterCollection(CreatureType.SHEEP));
        assertEquals(AnimalProductRules.MAX_COOLDOWN_SECONDS,
                AnimalProductRules.cooldownAfterCollection(CreatureType.SHEEP));
    }
}
