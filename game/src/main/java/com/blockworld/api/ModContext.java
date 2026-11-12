package com.blockworld.api;

import com.blockworld.core.GameVersion;
import com.blockworld.core.VoxelWorld;
import java.nio.file.Path;

/** Limited host context exposed to mods. The VoxelWorld API may evolve between releases. */
public record ModContext(VoxelWorld world, GameVersion version, Path configDirectory) { }
