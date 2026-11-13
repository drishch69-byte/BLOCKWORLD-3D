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
    void chestLootChangesWithWorldSeedOrChestPosition() {
        BlockPos firstChest = new BlockPos(18, -6, 42);
        Map<ItemType, Integer> first = TreasureLoot.roll(778899L, firstChest);
        Map<ItemType, Integer> anotherSeed = TreasureLoot.roll(778898L, firstChest);
        Map<ItemType, Integer> anotherPosition = TreasureLoot.roll(778899L, new BlockPos(19, -6, 42));
        assertTrue(!first.equals(anotherSeed) || !first.equals(anotherPosition));
    }
}