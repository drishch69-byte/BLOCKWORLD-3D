package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.VoxelWorld;
import com.jme3.asset.DesktopAssetManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerWorkplaceRulesTest {
    @Test
    void mapsEachProfessionToItsWorkplace() {
        assertTrue(VillagerWorkplaceRules.isWorkplaceFor(CreatureType.VILLAGER_FARMER, BlockType.FARMLAND));
        assertTrue(VillagerWorkplaceRules.isWorkplaceFor(CreatureType.VILLAGER_FARMER, BlockType.WHEAT_CROP));
        assertTrue(VillagerWorkplaceRules.isWorkplaceFor(CreatureType.VILLAGER_BLACKSMITH, BlockType.FURNACE));
        assertTrue(VillagerWorkplaceRules.isWorkplaceFor(CreatureType.VILLAGER_LIBRARIAN, BlockType.TREASURE_CHEST));
        assertTrue(VillagerWorkplaceRules.isWorkplaceFor(CreatureType.VILLAGER_LIBRARIAN, BlockType.BOOKSHELF));
        assertTrue(VillagerWorkplaceRules.isOtherWorkplaceFor(CreatureType.VILLAGER_FARMER, BlockType.FURNACE));
        assertFalse(VillagerWorkplaceRules.isOtherWorkplaceFor(CreatureType.VILLAGER_FARMER, BlockType.FARMLAND));
        assertFalse(VillagerWorkplaceRules.isWorkplaceFor(CreatureType.VILLAGER_FARMER, BlockType.FURNACE));
        assertFalse(VillagerWorkplaceRules.isWorkplaceFor(CreatureType.VILLAGER_GUARD, BlockType.TREASURE_CHEST));
    }

    @Test
    void findsNearestMatchingWorkplaceAndIgnoresOtherProfessions() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        BlockPos fartherField = new BlockPos(5, 0, 0);
        BlockPos nearbyFurnace = new BlockPos(1, 0, 0);
        assertTrue(world.addBlock(fartherField, BlockType.FARMLAND));
        assertTrue(world.addBlock(nearbyFurnace, BlockType.FURNACE));

        assertEquals(fartherField, VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_FARMER, 0.5f, 1f, 0.5f, world, 8));
        assertEquals(nearbyFurnace, VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_BLACKSMITH, 0.5f, 1f, 0.5f, world, 8));
        assertNull(VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_LIBRARIAN, 0.5f, 1f, 0.5f, world, 8));
    }

    @Test
    void findsLibrarianArchivesAndRespectsTheSearchRadius() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        BlockPos archive = new BlockPos(3, 0, 0);
        assertTrue(world.addBlock(archive, BlockType.TREASURE_CHEST));

        assertEquals(archive, VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_LIBRARIAN, 0.5f, 1f, 0.5f, world, 4));
        assertNull(VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_LIBRARIAN, 0.5f, 1f, 0.5f, world, 1));
    }

    @Test
    void workIsAvailableDuringTheDayButNotAtNight() {
        assertTrue(VillagerWorkplaceRules.shouldWork(CreatureType.VILLAGER_FARMER, 0.25f));
        assertTrue(VillagerWorkplaceRules.shouldWork(CreatureType.VILLAGER_BLACKSMITH, 0.40f));
        assertTrue(VillagerWorkplaceRules.shouldWork(CreatureType.VILLAGER_LIBRARIAN, 0.50f));
        assertFalse(VillagerWorkplaceRules.shouldWork(CreatureType.VILLAGER_FARMER, 0.70f));
        assertFalse(VillagerWorkplaceRules.shouldWork(CreatureType.VILLAGER, 0.25f));
        assertFalse(VillagerWorkplaceRules.shouldWork(CreatureType.VILLAGER_GUARD, 0.25f));
    }

    @Test
    void villagersCanVisitWorkplacesAssignedToOtherProfessions() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        BlockPos farm = new BlockPos(1, 0, 0);
        BlockPos smithy = new BlockPos(4, 0, 0);
        BlockPos library = new BlockPos(7, 0, 0);
        assertTrue(world.addBlock(farm, BlockType.FARMLAND));
        assertTrue(world.addBlock(smithy, BlockType.FURNACE));
        assertTrue(world.addBlock(library, BlockType.BOOKSHELF));

        assertEquals(smithy, VillagerWorkplaceRules.findNearestOtherWorkplace(
                CreatureType.VILLAGER_FARMER, 0.5f, 1f, 0.5f, world, 8));
        assertEquals(farm, VillagerWorkplaceRules.findNearestOtherWorkplace(
                CreatureType.VILLAGER_BLACKSMITH, 0.5f, 1f, 0.5f, world, 8));
        assertEquals(farm, VillagerWorkplaceRules.findNearestOtherWorkplace(
                CreatureType.VILLAGER_LIBRARIAN, 0.5f, 1f, 0.5f, world, 8));
        assertEquals(farm, VillagerWorkplaceRules.findNearestOtherWorkplace(
                CreatureType.VILLAGER, 0.5f, 1f, 0.5f, world, 8));
    }

    @Test
    void findsNearbyBedsForNightRestAndIgnoresDistantBeds() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        BlockPos bed = new BlockPos(3, 1, 0);
        assertTrue(world.addBlock(bed, BlockType.VILLAGER_BED));

        assertEquals(bed, VillagerWorkplaceRules.findNearestBed(
                0.5f, 1f, 0.5f, world, 4));
        assertNull(VillagerWorkplaceRules.findNearestBed(
                0.5f, 1f, 0.5f, world, 1));
        assertNull(VillagerWorkplaceRules.findNearestBed(
                Float.NaN, 1f, 0f, world, 4));
    }

    @Test
    void rejectsInvalidCoordinatesAndSearchRanges() {
        VoxelWorld world = new VoxelWorld(new DesktopAssetManager(true));
        assertNull(VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_FARMER, Float.NaN, 1f, 0f, world, 8));
        assertNull(VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_FARMER, 0f, 1f, 0f, world, -1));
        assertNull(VillagerWorkplaceRules.findNearestWorkplace(
                CreatureType.VILLAGER_FARMER, 0f, 1f, 0f, world, 65));
    }
}
