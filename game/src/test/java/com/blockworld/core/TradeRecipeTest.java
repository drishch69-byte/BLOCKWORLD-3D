package com.blockworld.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradeRecipeTest {
    @Test
    void tradeConsumesExactCostsAndAddsReward() {
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        items.put(ItemType.WHEAT, 5);
        TradeRecipe offer = TradeRecipe.villageOffers().get(0);
        assertTrue(offer.canTrade(items));
        assertTrue(offer.trade(items));
        assertEquals(1, items.get(ItemType.WHEAT));
        assertEquals(1, items.get(ItemType.EMERALD));
    }

    @Test
    void failedTradeDoesNotChangeInventory() {
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        items.put(ItemType.WHEAT, 3);
        Map<ItemType, Integer> before = new EnumMap<>(items);
        assertFalse(TradeRecipe.villageOffers().get(0).trade(items));
        assertEquals(before, items);
    }

    @Test
    void noCircularOfferReturnsBreadIntoWheat() {
        for (TradeRecipe offer : TradeRecipe.villageOffers()) {
            assertNotEquals(ItemType.WHEAT, offer.result());
        }
    }

    @Test
    void eachProfessionHasItsOwnFourOffers() {
        assertEquals(4, TradeRecipe.offersFor(CreatureType.VILLAGER_FARMER).size());
        assertEquals(4, TradeRecipe.offersFor(CreatureType.VILLAGER_BLACKSMITH).size());
        assertEquals(4, TradeRecipe.offersFor(CreatureType.VILLAGER_LIBRARIAN).size());
        assertEquals(4, TradeRecipe.offersFor(CreatureType.VILLAGER_GUARD).size());

        assertEquals(ItemType.WHEAT, TradeRecipe.offersFor(CreatureType.VILLAGER_FARMER).get(0).costs().keySet().iterator().next());
        assertEquals(ItemType.COPPER_INGOT, TradeRecipe.offersFor(CreatureType.VILLAGER_BLACKSMITH).get(0).costs().keySet().iterator().next());
        assertEquals(ItemType.EMERALD, TradeRecipe.offersFor(CreatureType.VILLAGER_LIBRARIAN).get(0).costs().keySet().iterator().next());
        assertEquals(ItemType.IRON_INGOT, TradeRecipe.offersFor(CreatureType.VILLAGER_GUARD).get(0).costs().keySet().iterator().next());
    }

    @Test
    void professionOffersCanBeCompletedAndInvalidInventoriesAreSafe() {
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        items.put(ItemType.COPPER_INGOT, 2);
        TradeRecipe offer = TradeRecipe.offersFor(CreatureType.VILLAGER_BLACKSMITH).get(0);

        assertTrue(offer.trade(items));
        assertEquals(0, items.get(ItemType.COPPER_INGOT));
        assertEquals(1, items.get(ItemType.EMERALD));
        assertFalse(offer.canTrade(null));
    }

    @Test
    void ordinaryVillagersKeepTheOriginalCatalogue() {
        assertEquals(TradeRecipe.villageOffers(), TradeRecipe.offersFor(CreatureType.VILLAGER));
        assertEquals(TradeRecipe.villageOffers(), TradeRecipe.offersFor(CreatureType.VILLAGER));
    }
}

