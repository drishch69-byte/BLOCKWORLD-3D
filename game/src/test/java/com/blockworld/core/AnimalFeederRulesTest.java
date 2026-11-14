package com.blockworld.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalFeederRulesTest {
    @Test
    void babiesUseTheSameFeedAsTheirAdultSpecies() {
        assertEquals(ItemType.WHEAT, AnimalFeederRules.feedFor(CreatureType.CALF));
        assertEquals(ItemType.WHEAT, AnimalFeederRules.feedFor(CreatureType.LAMB));
        assertEquals(ItemType.WHEAT, AnimalFeederRules.feedFor(CreatureType.PIGLET));
        assertEquals(ItemType.WHEAT_SEEDS, AnimalFeederRules.feedFor(CreatureType.CHICK));
        assertNull(AnimalFeederRules.feedFor(CreatureType.FOX));
    }

    @Test
    void refillsSeparateWheatAndSeedStocksWithoutExceedingCapacity() {
        var stock = AnimalFeederRules.refill(AnimalFeederRules.Stock.empty(), 80, 12);
        assertEquals(64, stock.wheat());
        assertEquals(12, stock.seeds());
        assertEquals(AnimalFeederRules.FEED_INTERVAL_SECONDS, stock.secondsUntilFeed());
        var toppedUp = AnimalFeederRules.refill(stock, 5, 80);
        assertEquals(64, toppedUp.wheat());
        assertEquals(64, toppedUp.seeds());
        assertEquals(stock, AnimalFeederRules.refill(stock, 0, 0));
    }

    @Test
    void animalsConsumeOnlyTheirMatchingFoodAfterTheTimerExpires() {
        var stock = AnimalFeederRules.refill(AnimalFeederRules.Stock.empty(), 3, 2);
        assertTrue(AnimalFeederRules.hasFood(stock, CreatureType.COW));
        assertTrue(AnimalFeederRules.hasFood(stock, CreatureType.CHICKEN));
        assertEquals(stock, AnimalFeederRules.consume(stock, CreatureType.COW));
        var ready = AnimalFeederRules.advance(stock, AnimalFeederRules.FEED_INTERVAL_SECONDS);
        var afterCow = AnimalFeederRules.consume(ready, CreatureType.COW);
        assertEquals(2, afterCow.wheat());
        assertEquals(2, afterCow.seeds());
        var afterChick = AnimalFeederRules.consume(ready, CreatureType.CHICK);
        assertEquals(3, afterChick.wheat());
        assertEquals(1, afterChick.seeds());
        assertEquals(AnimalFeederRules.FEED_INTERVAL_SECONDS, afterCow.secondsUntilFeed());
    }

    @Test
    void rejectsInvalidFeederStocks() {
        assertThrows(IllegalArgumentException.class, () -> new AnimalFeederRules.Stock(-1, 0, 10f));
        assertThrows(IllegalArgumentException.class, () -> new AnimalFeederRules.Stock(0, 65, 10f));
        assertThrows(IllegalArgumentException.class, () -> new AnimalFeederRules.Stock(1, 0, Float.NaN));
    }
}
