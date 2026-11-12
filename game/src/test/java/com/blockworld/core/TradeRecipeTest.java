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
}
