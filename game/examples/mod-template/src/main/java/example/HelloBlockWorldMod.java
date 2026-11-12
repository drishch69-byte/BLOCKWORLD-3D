package example;

import com.blockworld.api.BlockWorldMod;
import com.blockworld.api.ModContext;
import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.VoxelWorld;

/** Tiny example: places one decorative block when a world has finished loading. */
public final class HelloBlockWorldMod implements BlockWorldMod {
    private VoxelWorld world;

    @Override
    public void onInitialize(ModContext context) {
        world = context.world();
    }

    @Override
    public void onWorldReady() {
        int x = 4;
        int z = 4;
        int y = world.highestBlockY(x, z) + 1;
        world.addBlock(new BlockPos(x, y, z), BlockType.GOLD_ORE);
    }
}
