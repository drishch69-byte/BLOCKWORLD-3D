package com.blockworld.core;

/** Stable biome IDs used by the deterministic world generator. */
public enum BiomeType {
    PLAINS("Равнины", BlockType.GRASS),
    FOREST("Лес", BlockType.GRASS),
    DESERT("Пустыня", BlockType.SAND),
    SNOWY_TUNDRA("Снежная тундра", BlockType.SNOW),
    MOUNTAINS("Горы", BlockType.STONE);

    private final String displayName;
    private final BlockType surfaceBlock;

    BiomeType(String displayName, BlockType surfaceBlock) {
        this.displayName = displayName;
        this.surfaceBlock = surfaceBlock;
    }

    public String displayName() { return displayName; }
    public BlockType surfaceBlock() { return surfaceBlock; }
}
