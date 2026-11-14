package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.VoxelWorld;

/** Deterministic links between villager professions and existing village workstations. */
final class VillagerWorkplaceRules {
    static final int SEARCH_RADIUS = 10;
    static final float SEARCH_INTERVAL_SECONDS = 4.5f;

    private VillagerWorkplaceRules() {
    }

    static boolean shouldWork(CreatureType type, float dayPhase) {
        return hasWorkplace(type) && !VillagerBehaviorRules.shouldRest(type, dayPhase);
    }

    static boolean hasWorkplace(CreatureType type) {
        return type == CreatureType.VILLAGER_FARMER
                || type == CreatureType.VILLAGER_BLACKSMITH
                || type == CreatureType.VILLAGER_LIBRARIAN;
    }

    static boolean isWorkplaceFor(CreatureType type, BlockType block) {
        if (type == null || block == null) return false;
        return switch (type) {
            case VILLAGER_FARMER -> block == BlockType.FARMLAND || block == BlockType.WHEAT_CROP;
            case VILLAGER_BLACKSMITH -> block == BlockType.FURNACE;
            // The existing village archive chest serves as the librarian's book archive.
            case VILLAGER_LIBRARIAN -> block == BlockType.TREASURE_CHEST;
            default -> false;
        };
    }

    static BlockPos findNearestWorkplace(CreatureType type, float x, float y, float z,
                                         VoxelWorld world, int radius) {
        if (!hasWorkplace(type) || world == null || !Float.isFinite(x)
                || !Float.isFinite(y) || !Float.isFinite(z) || radius < 0 || radius > 64) {
            return null;
        }
        int centerX = (int) Math.floor(x);
        int centerY = (int) Math.floor(y);
        int centerZ = (int) Math.floor(z);
        float bestDistance = radius * radius;
        BlockPos best = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos candidate = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                    if (!isWorkplaceFor(type, world.getBlock(candidate))) continue;
                    float distanceX = candidate.x() + 0.5f - x;
                    float distanceZ = candidate.z() + 0.5f - z;
                    float distance = distanceX * distanceX + distanceZ * distanceZ;
                    if (distance <= bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }
}
