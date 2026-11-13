package com.blockworld.core;

import java.util.EnumSet;

/** Stable biome IDs used by the deterministic world generator. */
public enum BiomeType {
    PLAINS("Равнины", BlockType.GRASS),
    FOREST("Лес", BlockType.GRASS),
    DESERT("Пустыня", BlockType.SAND),
    SNOWY_TUNDRA("Снежная тундра", BlockType.SNOW),
    MOUNTAINS("Горы", BlockType.STONE),
    TAIGA("Тайга", BlockType.PODZOL),
    SWAMP("Болото", BlockType.MUD),
    ENCHANTED_GROVE("Зачарованная роща", BlockType.CAVE_MOSS);

    private final String displayName;
    private final BlockType surfaceBlock;

    BiomeType(String displayName, BlockType surfaceBlock) {
        this.displayName = displayName;
        this.surfaceBlock = surfaceBlock;
    }

    public String displayName() { return displayName; }
    public BlockType surfaceBlock() { return surfaceBlock; }

    public static EnumSet<BiomeType> legacySet() {
        return EnumSet.of(PLAINS, FOREST, DESERT, SNOWY_TUNDRA, MOUNTAINS);
    }

    public static EnumSet<BiomeType> extendedSet() {
        EnumSet<BiomeType> result = legacySet();
        result.add(TAIGA);
        result.add(SWAMP);
        return result;
    }

    public static EnumSet<BiomeType> enchantedSet() {
        EnumSet<BiomeType> result = extendedSet();
        result.add(ENCHANTED_GROVE);
        return result;
    }
}
