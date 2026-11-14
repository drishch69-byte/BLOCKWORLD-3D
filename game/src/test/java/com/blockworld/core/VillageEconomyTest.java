package com.blockworld.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VillageEconomyTest {
    @Test
    void buyingConsumesMarketStockAndAddsPaymentToTheVillage() {
        VillageEconomy economy = new VillageEconomy();
        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        inventory.put(ItemType.WHEAT, 4);
        TradeRecipe offer = TradeRecipe.offersFor(CreatureType.VILLAGER_FARMER).get(0);

        assertTrue(economy.canTrade(CreatureType.VILLAGER_FARMER, offer, inventory));
        assertTrue(economy.trade(CreatureType.VILLAGER_FARMER, offer, inventory));
        assertEquals(0, inventory.get(ItemType.WHEAT));
        assertEquals(1, inventory.get(ItemType.EMERALD));
        assertEquals(7, economy.stock(CreatureType.VILLAGER_FARMER, ItemType.EMERALD));
        assertEquals(4, economy.stock(CreatureType.VILLAGER_FARMER, ItemType.WHEAT));
    }

    @Test
    void marketRefusesTradesWhenItsRewardStockIsEmpty() {
        VillageEconomy economy = new VillageEconomy();
        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        inventory.put(ItemType.EMERALD, 1);
        TradeRecipe offer = TradeRecipe.offersFor(CreatureType.VILLAGER_LIBRARIAN).get(0);
        economy.setStock(CreatureType.VILLAGER_LIBRARIAN, ItemType.TORCH, 0);

        assertFalse(economy.canTrade(CreatureType.VILLAGER_LIBRARIAN, offer, inventory));
        assertFalse(economy.trade(CreatureType.VILLAGER_LIBRARIAN, offer, inventory));
        assertEquals(1, inventory.get(ItemType.EMERALD));
    }

    @Test
    void workersRestockTheirProfessionAndSmithSuppliesTheGuardPost() {
        VillageEconomy economy = new VillageEconomy();
        Map<CreatureType, Integer> workers = new EnumMap<>(CreatureType.class);
        workers.put(CreatureType.VILLAGER_FARMER, 1);
        workers.put(CreatureType.VILLAGER_BLACKSMITH, 1);
        workers.put(CreatureType.VILLAGER_LIBRARIAN, 1);

        assertTrue(economy.advanceWork(workers, 60f));
        assertEquals(7, economy.stock(CreatureType.VILLAGER_FARMER, ItemType.BREAD));
        assertEquals(9, economy.stock(CreatureType.VILLAGER_FARMER, ItemType.EMERALD));
        assertEquals(12, economy.stock(CreatureType.VILLAGER_BLACKSMITH, ItemType.IRON_INGOT));
        assertEquals(20, economy.stock(CreatureType.VILLAGER_LIBRARIAN, ItemType.TORCH));
        assertEquals(3, economy.stock(CreatureType.VILLAGER_GUARD, ItemType.IRON_HELMET));
    }

    @Test
    void productionIsBoundedAndInvalidOrMissingWorkersDoNotProduce() {
        VillageEconomy economy = new VillageEconomy();
        assertFalse(economy.advanceWork(null, 10f));
        assertFalse(economy.advanceWork(Map.of(CreatureType.VILLAGER_FARMER, 1), Float.NaN));
        assertFalse(economy.advanceWork(Map.of(CreatureType.VILLAGER_FARMER, -1), 60f));
        assertEquals(8, economy.stock(CreatureType.VILLAGER_FARMER, ItemType.EMERALD));

        economy.setStock(CreatureType.VILLAGER_FARMER, ItemType.BREAD, 64);
        economy.advanceWork(Map.of(CreatureType.VILLAGER_FARMER, 4), 30f);
        assertEquals(64, economy.stock(CreatureType.VILLAGER_FARMER, ItemType.BREAD));
    }

    @Test
    void snapshotsRestoreStocksAndUnfinishedWorkProgress() {
        VillageEconomy economy = new VillageEconomy();
        economy.setStock(CreatureType.VILLAGER_FARMER, ItemType.BREAD, 17);
        economy.advanceWork(Map.of(CreatureType.VILLAGER_FARMER, 1), 25f);

        VillageEconomy restored = new VillageEconomy();
        restored.restore(economy.snapshot());

        assertEquals(17, restored.stock(CreatureType.VILLAGER_FARMER, ItemType.BREAD));
        assertEquals(25f, restored.workProgress(CreatureType.VILLAGER_FARMER));
    }
}
