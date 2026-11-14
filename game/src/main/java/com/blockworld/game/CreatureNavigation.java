package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.VoxelWorld;

/** Collision-aware ground movement shared by local creature AI. */
final class CreatureNavigation {
    private static final int INVALID_SURFACE = Integer.MIN_VALUE;
    private static final float MIN_STEP = 0.00001f;

    private CreatureNavigation() { }

    static Step resolveStep(CreatureType type, float fromX, float fromY, float fromZ,
                            float desiredX, float desiredZ, VoxelWorld world) {
        int directSurface = walkableSurfaceY(type, desiredX, desiredZ, fromY, world);
        if (directSurface != INVALID_SURFACE) {
            return new Step(desiredX, directSurface + 1f, desiredZ);
        }

        float deltaX = desiredX - fromX;
        float deltaZ = desiredZ - fromZ;
        int xSurface = Math.abs(deltaX) > MIN_STEP
                ? walkableSurfaceY(type, desiredX, fromZ, fromY, world) : INVALID_SURFACE;
        int zSurface = Math.abs(deltaZ) > MIN_STEP
                ? walkableSurfaceY(type, fromX, desiredZ, fromY, world) : INVALID_SURFACE;

        // Sliding along one axis lets creatures pass corners instead of repeatedly
        // trying the same blocked diagonal position.
        if (xSurface != INVALID_SURFACE && zSurface != INVALID_SURFACE) {
            return Math.abs(deltaX) >= Math.abs(deltaZ)
                    ? new Step(desiredX, xSurface + 1f, fromZ)
                    : new Step(fromX, zSurface + 1f, desiredZ);
        }
        if (xSurface != INVALID_SURFACE) return new Step(desiredX, xSurface + 1f, fromZ);
        if (zSurface != INVALID_SURFACE) return new Step(fromX, zSurface + 1f, desiredZ);

        int currentSurface = walkableSurfaceY(type, fromX, fromZ, fromY, world);
        return currentSurface == INVALID_SURFACE
                ? null : new Step(fromX, currentSurface + 1f, fromZ);
    }

    private static int walkableSurfaceY(CreatureType type, float x, float z,
                                        float currentY, VoxelWorld world) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        boolean caveSentinel = type == CreatureType.CRYSTAL_SENTINEL;
        boolean templeWarden = type == CreatureType.TEMPLE_WARDEN;
        int surfaceY = caveSentinel
                ? world.highestBlockYAtOrBelow(blockX, blockZ, (int) Math.floor(currentY - 0.2f))
                : world.highestBlockY(blockX, blockZ);
        if (surfaceY < -12 || Math.abs((surfaceY + 1f) - currentY) > 1.25f) return INVALID_SURFACE;

        BlockType surface = world.getBlock(new BlockPos(blockX, surfaceY, blockZ));
        boolean safeSurface = caveSentinel
                ? surface == BlockType.STONE || surface == BlockType.AMETHYST_ORE
                    || surface == BlockType.GLOWING_CRYSTAL || surface == BlockType.RUBY_ORE
                    || surface == BlockType.SAPPHIRE_ORE
                : surface == BlockType.GRASS || surface == BlockType.DIRT
                    || surface == BlockType.SAND || surface == BlockType.PODZOL
                    || surface == BlockType.MUD || surface == BlockType.SNOW
                    || (templeWarden && surface == BlockType.SANDSTONE);
        if (!safeSurface) return INVALID_SURFACE;

        boolean clearAbove = !world.contains(new BlockPos(blockX, surfaceY + 1, blockZ))
                && !world.contains(new BlockPos(blockX, surfaceY + 2, blockZ))
                && (!(caveSentinel || templeWarden)
                    || !world.contains(new BlockPos(blockX, surfaceY + 3, blockZ)));
        return clearAbove ? surfaceY : INVALID_SURFACE;
    }

    record Step(float x, float y, float z) { }
}
