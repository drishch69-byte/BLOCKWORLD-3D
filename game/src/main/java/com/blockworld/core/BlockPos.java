package com.blockworld.core;

public record BlockPos(int x, int y, int z) {
    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
