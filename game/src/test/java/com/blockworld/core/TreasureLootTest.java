package com.blockworld.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TreasureLootTest {
    @Test
    void chestLootIsStableForTheSameWorldAndPosition() {
        BlockPos chest = new BlockPos(18, -6, 42);
        Map<ItemType, Integer> first = TreasureLoot.roll(778899L, chest);
        Map<ItemType, Integer> second = TreasureLoot.roll(778899L, chest);
        assertEquals(first, second);
        assertFalse(first.isEmpty());
        assertTrue(first.values().stream().allMatch(count -> count > 0));
    }

    @Test
    void undergroundCrystalCachesGuaranteeEmeraldsAndExtraSupplies() {
        BlockPos caveCache = new BlockPos(74, -9, -31);
        Map<ItemType, Integer> first = TreasureLoot.roll(551122L, caveCache);
        Map<ItemType, Integer> second = TreasureLoot.roll(551122L, caveCache);
        assertEquals(first, second);
        assertTrue(first.getOrDefault(ItemType.EMERALD, 0) >= 1);
        assertTrue(first.getOrDefault(ItemType.IRON_INGOT, 0) >= 3
                || first.getOrDefault(ItemType.COPPER_INGOT, 0) >= 3);
    }

    @Test
    void crystalCacheLootIsStableAndIncludesRareSupplies() {
        BlockPos cache = new BlockPos(74, -9, -31);
        Map<ItemType, Integer> first = TreasureLoot.rollCrystalCache(551122L, cache);
        Map<ItemType, Integer> second = TreasureLoot.rollCrystalCache(551122L, cache);
        assertEquals(first, second);
        assertTrue(first.getOrDefault(ItemType.EMERALD, 0) >= 2);
        assertTrue(first.getOrDefault(ItemType.COOKED_FISH, 0) >= 1);
        assertTrue(first.getOrDefault(ItemType.IRON_INGOT, 0) >= 4
                || first.getOrDefault(ItemType.COPPER_INGOT, 0) >= 4);
        assertThrows(IllegalArgumentException.class, () -> TreasureLoot.rollCrystalCache(1L, null));
    }

    @Test
    void chestLootChangesWithWorldSeedOrChestPosition() {
        BlockPos firstChest = new BlockPos(18, -6, 42);
        Map<ItemType, Integer> first = TreasureLoot.roll(778899L, firstChest);
        Map<ItemType, Integer> anotherSeed = TreasureLoot.roll(778898L, firstChest);
        Map<ItemType, Integer> anotherPosition = TreasureLoot.roll(778899L, new BlockPos(19, -6, 42));
        assertTrue(!first.equals(anotherSeed) || !first.equals(anotherPosition));
    }
}