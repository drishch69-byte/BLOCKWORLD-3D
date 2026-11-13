package com.blockworld.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Seeded, position-specific loot for one-time exploration chests. */
public final class TreasureLoot {
    private TreasureLoot() { }

    /** Richer one-time reward table used only by 1.9.7 crystal caches. */
    public static Map<ItemType, Integer> rollCrystalCache(long seed, BlockPos chest) {
        if (chest == null) throw new IllegalArgumentException("Позиция тайника обязательна");
        long mixedSeed = seed
                ^ ((long) chest.x() * 0x9E3779B97F4A7C15L)
                ^ ((long) chest.y() * 0xC2B2AE3D27D4EB4FL)
                ^ ((long) chest.z() * 0x165667B19E3779F9L)
                ^ 0x6A09E667F3BCC909L;
        Random random = new Random(mixedSeed);
        EnumMap<ItemType, Integer> loot = new EnumMap<>(ItemType.class);
        loot.put(ItemType.EMERALD, 2 + random.nextInt(3));
        if (random.nextBoolean()) loot.put(ItemType.IRON_INGOT, 4 + random.nextInt(3));
        else loot.put(ItemType.COPPER_INGOT, 4 + random.nextInt(4));
        loot.put(ItemType.COOKED_FISH, 1 + random.nextInt(2));
        return Collections.unmodifiableMap(loot);
    }

    /** Deterministic artifact reward table for newly generated 1.9.8 temples. */
    public static Map<ItemType, Integer> rollAncientCache(long seed, BlockPos cache) {
        if (cache == null) throw new IllegalArgumentException("Позиция древнего тайника обязательна");
        long mixedSeed = seed
                ^ ((long) cache.x() * 0x9E3779B97F4A7C15L)
                ^ ((long) cache.y() * 0xC2B2AE3D27D4EB4FL)
                ^ ((long) cache.z() * 0x165667B19E3779F9L)
                ^ 0x3C6EF372FE94F82BL;
        Random random = new Random(mixedSeed);
        EnumMap<ItemType, Integer> loot = new EnumMap<>(ItemType.class);
        loot.put(ItemType.ANCIENT_RELIC, 1);
        loot.put(random.nextBoolean() ? ItemType.SUN_SIGIL : ItemType.MOON_SIGIL, 1);
        loot.put(ItemType.EMERALD, 3 + random.nextInt(4));
        if (random.nextBoolean()) loot.put(ItemType.IRON_INGOT, 5 + random.nextInt(3));
        else loot.put(ItemType.COPPER_INGOT, 5 + random.nextInt(4));
        return Collections.unmodifiableMap(loot);
    }

    public static Map<ItemType, Integer> roll(long seed, BlockPos chest) {
        if (chest == null) throw new IllegalArgumentException("Позиция сундука обязательна");
        long mixedSeed = seed
                ^ ((long) chest.x() * 0x9E3779B97F4A7C15L)
                ^ ((long) chest.y() * 0xC2B2AE3D27D4EB4FL)
                ^ ((long) chest.z() * 0x165667B19E3779F9L);
        Random random = new Random(mixedSeed);
        EnumMap<ItemType, Integer> loot = new EnumMap<>(ItemType.class);

        int roll = random.nextInt(100);
        if (roll < 12) {
            loot.put(ItemType.EMERALD, 1 + random.nextInt(2));
        } else if (roll < 42) {
            loot.put(ItemType.IRON_INGOT, 2 + random.nextInt(3));
        } else if (roll < 70) {
            loot.put(ItemType.COPPER_INGOT, 2 + random.nextInt(4));
        } else {
            loot.put(ItemType.BREAD, 1 + random.nextInt(2));
        }

        if (random.nextBoolean()) {
            loot.merge(ItemType.COOKED_FISH, 1, Integer::sum);
        } else if (random.nextInt(4) == 0) {
            loot.merge(ItemType.BREAD, 1, Integer::sum);
        }
        return Collections.unmodifiableMap(loot);
    }
}
