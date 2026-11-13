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
    void ancientTempleCacheAlwaysContainsAnArtifactAndASeededSigil() {
        BlockPos cache = new BlockPos(124, 8, -126);
        Map<ItemType, Integer> first = TreasureLoot.rollAncientCache(20261114L, cache);
        Map<ItemType, Integer> second = TreasureLoot.rollAncientCache(20261114L, cache);
        assertEquals(first, second);
        assertEquals(1, first.getOrDefault(ItemType.ANCIENT_RELIC, 0));
        assertEquals(1, first.getOrDefault(ItemType.SUN_SIGIL, 0)
                + first.getOrDefault(ItemType.MOON_SIGIL, 0));
        assertTrue(first.getOrDefault(ItemType.EMERALD, 0) >= 3);
        assertTrue(first.getOrDefault(ItemType.IRON_INGOT, 0) >= 5
                || first.getOrDefault(ItemType.COPPER_INGOT, 0) >= 5);
        assertThrows(IllegalArgumentException.class, () -> TreasureLoot.rollAncientCache(1L, null));
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