package com.blockworld.core;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VoxelWorldChunkTest {
    @Test
    void storesBlocksInSixteenBySixteenChunksIncludingNegativeCoordinates() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        assertTrue(world.isTwoSidedRendering());
        world.setTwoSidedRendering(false);
        assertTrue(!world.isTwoSidedRendering());
        world.setTwoSidedRendering(true);

        assertTrue(world.addBlock(new BlockPos(-1, 0, -1), BlockType.GRASS));
        assertTrue(world.addBlock(new BlockPos(0, 0, -1), BlockType.GRASS));
        assertTrue(world.addBlock(new BlockPos(15, 0, 0), BlockType.DIRT));
        assertTrue(world.addBlock(new BlockPos(16, 0, 0), BlockType.STONE));

        assertEquals(4, world.getBlockCount());
        assertEquals(4, world.getChunkCount());
    }

    @Test
    void cullsSharedFaceBetweenAdjacentChunksWhenBothAreInRenderRange() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        assertTrue(world.addBlock(new BlockPos(15, 0, 0), BlockType.STONE));
        assertTrue(world.addBlock(new BlockPos(16, 0, 0), BlockType.STONE));

        world.setRenderDistanceChunks(2);
        world.updateStreaming(new Vector3f(15f, 4f, 0f), 4);

        assertEquals(2, world.getRenderedChunkCount());
        int totalVertices = 0;
        for (int i = 0; i < world.getNode().getQuantity(); i++) {
            Geometry geometry = (Geometry) world.getNode().getChild(i);
            totalVertices += geometry.getMesh().getVertexCount();
        }
        // Ten visible faces total: the two blocks do not render their shared border face.
        assertEquals(40, totalVertices);
    }

    @Test
    void buildsOneCombinedMeshAndMapsItsTriangleBackToTheBlockFace() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        BlockPos block = new BlockPos(0, 0, 0);
        assertTrue(world.addBlock(block, BlockType.GRASS));

        world.updateStreaming(new Vector3f(0f, 4f, 5f), 1);

        assertEquals(1, world.getRenderedChunkCount());
        Geometry chunkGeometry = (Geometry) world.getNode().getChild(0);
        BlockHit hit = world.getHitFace(chunkGeometry, 0);
        assertNotNull(hit);
        assertEquals(block, hit.position());
        assertEquals(1, hit.normalX());
    }

    @Test
    void removingHighestBlockRecalculatesOnlyItsColumnHeight() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        BlockPos lower = new BlockPos(-17, -4, -1);
        BlockPos upper = new BlockPos(-17, 6, -1);

        assertTrue(world.addBlock(lower, BlockType.STONE));
        assertTrue(world.addBlock(upper, BlockType.GRASS));
        for (int x = 0; x < 48; x++) {
            assertTrue(world.addBlock(new BlockPos(x, 0, 32), BlockType.DIRT));
        }
        assertEquals(6, world.highestBlockY(-17, -1));

        assertTrue(world.removeBlock(upper));
        assertEquals(-4, world.highestBlockY(-17, -1));
        assertTrue(world.removeBlock(lower));
        assertEquals(-13, world.highestBlockY(-17, -1));
    }

    @Test
    void findsSpawnNearExistingTerrainInsteadOfAssumingWorldOrigin() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        world.addBlock(new BlockPos(100, 0, 100), BlockType.STONE);
        world.addBlock(new BlockPos(-2, 0, 7), BlockType.DIRT);
        world.addBlock(new BlockPos(-2, 3, 7), BlockType.GRASS);

        Vector3f spawn = world.getSuggestedSpawn();
        assertEquals(-2f, spawn.x);
        assertEquals(4.68f, spawn.y, 0.0001f);
        assertEquals(7f, spawn.z);
    }


    @Test
    void prefersAnOpenGrassSpawnColumnOverATreeCanopy() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        world.addBlock(new BlockPos(0, 0, 8), BlockType.GRASS);
        world.addBlock(new BlockPos(0, 1, 8), BlockType.WOOD);
        world.addBlock(new BlockPos(0, 2, 8), BlockType.LEAVES);
        world.addBlock(new BlockPos(-2, 0, 7), BlockType.GRASS);

        Vector3f spawn = world.getSuggestedSpawn();
        assertEquals(-2f, spawn.x);
        assertEquals(1.68f, spawn.y, 0.0001f);
        assertEquals(7f, spawn.z);
    }

    @Test
    void chunkEngineProfileGeneratesAWiderWorldThanSurvivalPreview() {
        AssetManager assets = new DesktopAssetManager(true);
        VoxelWorld world = new VoxelWorld(assets);
        GameVersion chunkVersion = VersionManager.find("0.5.0");
        world.generate(chunkVersion, 42L);

        assertEquals(64, chunkVersion.worldRadius());
        assertTrue(world.getBlockCount() > 100_000);
        assertTrue(world.getChunkCount() > 40);
        assertEquals(42L, world.getSeed());
    }
}
