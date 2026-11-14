package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.VoxelWorld;
import com.blockworld.core.WorldCreature;
import com.jme3.asset.AssetManager;
import com.jme3.system.DesktopAssetManager;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatureManagerCombatTest {
    @Test
    void aMobCanBeHitBeforeACloserWallButNotThroughAWall() {
        String clearShot = attackWithWallAt(-3);
        String blockedShot = attackWithWallAt(-1);

        assertNotNull(clearShot, "The mob is in front of the wall and should be hit");
        assertNull(blockedShot, "A wall between the player and the mob must block the hit");
    }

    private String attackWithWallAt(int wallZ) {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        assertTrue(world.addBlock(new BlockPos(0, 0, wallZ), BlockType.STONE));
        assertTrue(world.addCreature(new WorldCreature(7, CreatureType.ZOMBIE,
                0.5f, 0f, -1.5f, 0f)));

        CreatureManager creatures = new CreatureManager(assets);
        creatures.sync(world);
        world.updateStreaming(new Vector3f(0.5f, 1.7f, 0.5f), 4);

        return creatures.attackAlongRay(
                new Vector3f(0.5f, 0.72f, 0.5f),
                new Vector3f(0f, 0f, -1f),
                3.4f, 0.82f, 2.5f, world);
    }
}
