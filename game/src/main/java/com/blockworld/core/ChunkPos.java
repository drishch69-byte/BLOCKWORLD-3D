package com.blockworld.core;

/** Horizontal 16x16 voxel chunk coordinates. floorDiv keeps negative worlds aligned. */
public record ChunkPos(int x, int z) {
    public static final int SIZE = 16;

    public static ChunkPos fromBlock(int blockX, int blockZ) {
        return new ChunkPos(Math.floorDiv(blockX, SIZE), Math.floorDiv(blockZ, SIZE));
    }

    public int minBlockX() {
        return x * SIZE;
    }

    public int minBlockZ() {
        return z * SIZE;
    }
}
