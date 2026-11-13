package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.VoxelWorld;
import com.jme3.asset.DesktopAssetManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NatureDecorationManagerTest {
    @Test
    void batchesMeadowPlantsIntoAtMostOneGeometry() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                world.addBlock(new BlockPos(x, 0, z), BlockType.GRASS);
            }
        }

        NatureDecorationManager manager = new NatureDecorationManager(new DesktopAssetManager(true));
        manager.rebuild(world);

        assertTrue(manager.size() > 0, "Expected seeded meadow decorations");
        assertTrue(manager.getNode().getChildren().size() <= 1,
                "Plants should share a single batched geometry");
    }
}
