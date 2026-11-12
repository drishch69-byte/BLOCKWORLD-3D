package com.blockworld.core;

/** The block and outward face hit by a ray against an aggregated chunk mesh. */
public record BlockHit(BlockPos position, int normalX, int normalY, int normalZ) {
    public BlockPos adjacentPosition() {
        return position.add(normalX, normalY, normalZ);
    }
}
