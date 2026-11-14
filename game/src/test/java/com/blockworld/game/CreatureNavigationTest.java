package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.VoxelWorld;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatureNavigationTest {
    @Test
    void openGateAllowsCreaturesThroughWhileClosedGateBlocksTheDoorway() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                world.addBlock(new BlockPos(x, 0, z), BlockType.GRASS);
            }
        }
        BlockPos gate = new BlockPos(1, 1, 0);
        assertTrue(world.addBlock(gate, BlockType.WOODEN_GATE));

        CreatureNavigation.Step closed = CreatureNavigation.resolveStep(
                CreatureType.COW, 0.5f, 1f, 0.5f, 1.1f, 0.5f, world);
        assertNotNull(closed);
        assertEquals(0.5f, closed.x(), 0.0001f);
        assertTrue(world.removeBlock(gate));
        assertTrue(world.addBlock(gate, BlockType.OPEN_WOODEN_GATE));

        assertFalse(world.contains(gate), "An open gate must not block collision checks");
        assertEquals(0, world.highestBlockY(1, 0), "The gate must not become the floor");
        CreatureNavigation.Step opened = CreatureNavigation.resolveStep(
                CreatureType.COW, 0.5f, 1f, 0.5f, 1.1f, 0.5f, world);
        assertNotNull(opened);
        assertEquals(1.1f, opened.x(), 0.0001f);
        assertEquals(1f, opened.y(), 0.0001f);
    }

    @Test
    void hostileCreatureSlidesAlongOpenAxisWhenDiagonalIsBlockedByWall() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        for (int x = -2; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                world.addBlock(new BlockPos(x, 0, z), BlockType.GRASS);
            }
        }
        world.addBlock(new BlockPos(1, 1, 0), BlockType.STONE);

        CreatureNavigation.Step step = CreatureNavigation.resolveStep(
                CreatureType.ZOMBIE, 0.5f, 1f, 0.5f, 1.1f, 0.7f, world);

        assertNotNull(step);
        assertEquals(0.5f, step.x(), 0.0001f);
        assertEquals(0.7f, step.z(), 0.0001f);
        assertEquals(1f, step.y(), 0.0001f);
    }

    @Test
    void creatureStopsAtWallWithoutMovingThroughIt() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                world.addBlock(new BlockPos(x, 0, z), BlockType.GRASS);
            }
        }
        world.addBlock(new BlockPos(1, 1, 0), BlockType.STONE);

        CreatureNavigation.Step step = CreatureNavigation.resolveStep(
                CreatureType.ZOMBIE, 0.5f, 1f, 0.5f, 1.1f, 0.5f, world);

        assertNotNull(step);
        assertEquals(0.5f, step.x(), 0.0001f);
        assertEquals(0.5f, step.z(), 0.0001f);
        assertEquals(1f, step.y(), 0.0001f);
    }
}
