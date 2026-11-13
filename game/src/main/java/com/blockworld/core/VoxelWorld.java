package com.blockworld.core;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

/** Block data plus streamed, face-culled 16x16 chunk meshes with optional atlas UVs. */
public final class VoxelWorld {
    private static final String FILE_HEADER = "BLOCKWORLD_WORLD";
    private static final int MAX_SAVED_BLOCKS = 1_000_000;
    private static final int DEFAULT_RENDER_DISTANCE = 4;
    private static final int MAX_RENDER_DISTANCE = 12;

    private static final int[][] QUAD_UV_CORNERS = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};

    private static final Face[] FACES = {
            new Face(1, 0, 0, 1.00f, new float[]{1,0,0, 1,1,0, 1,1,1, 1,0,1}),
            new Face(-1, 0, 0, 0.78f, new float[]{0,0,0, 0,0,1, 0,1,1, 0,1,0}),
            new Face(0, 1, 0, 1.12f, new float[]{0,1,0, 0,1,1, 1,1,1, 1,1,0}),
            new Face(0, -1, 0, 0.58f, new float[]{0,0,0, 1,0,0, 1,0,1, 0,0,1}),
            new Face(0, 0, 1, 0.86f, new float[]{0,0,1, 1,0,1, 1,1,1, 0,1,1}),
            new Face(0, 0, -1, 0.70f, new float[]{0,0,0, 0,1,0, 1,1,0, 1,0,0})
    };

    private final Node node = new Node("VoxelWorld");
    private final Map<BlockPos, BlockType> blocks = new HashMap<>();
    private final List<WorldCreature> creatures = new ArrayList<>();
    private final Map<BlockPos, String> preservedUnknownIds = new HashMap<>();
    private final Map<ChunkPos, Set<BlockPos>> chunkBlocks = new HashMap<>();
    private final Map<ChunkPos, Geometry> chunkGeometries = new HashMap<>();
    private final Map<Geometry, List<BlockHit>> triangleHits = new IdentityHashMap<>();
    private final Map<Long, Integer> columnHeights = new HashMap<>();
    private final Material chunkMaterial;
    private final boolean texturedChunks;
    private final EnumMap<BlockType, ColorRGBA> blockPalette = new EnumMap<>(BlockType.class);
    private final LinkedHashSet<ChunkPos> pendingChunks = new LinkedHashSet<>();
    private final Set<ChunkPos> desiredChunks = new HashSet<>();
    private long seed = 20261112L;
    private int renderDistanceChunks = DEFAULT_RENDER_DISTANCE;
    private ChunkPos lastStreamCenter;
    private boolean bulkUpdate;
    private boolean hasBounds;
    private String lastMeshError = "";
    private boolean twoSidedRendering = true;
    private boolean activeBiomes;
    private boolean extendedBiomes;
    private boolean caveBiomesEnabled;
    private boolean enchantedBiomeEnabled;
    private int worldRadius;
    private List<WorldNavigation.Landmark> landmarks = List.of();
    private int minBlockX;
    private int maxBlockX;
    private int minBlockZ;
    private int maxBlockZ;

    public VoxelWorld(AssetManager assetManager) { this(assetManager, Map.of(), null); }

    public VoxelWorld(AssetManager assetManager, Map<BlockType, ColorRGBA> palette) {
        this(assetManager, palette, null);
    }

    public VoxelWorld(AssetManager assetManager, Map<BlockType, ColorRGBA> palette, Texture2D textureAtlas) {
        Objects.requireNonNull(assetManager, "assetManager");
        if (palette != null) palette.forEach((type, color) -> blockPalette.put(type, color.clone()));
        texturedChunks = textureAtlas != null;
        chunkMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        chunkMaterial.setBoolean("VertexColor", true);
        chunkMaterial.setColor("Color", ColorRGBA.White);
        if (textureAtlas != null) chunkMaterial.setTexture("ColorMap", textureAtlas);
        // Keep both sides visible in this diagnostic build. This also makes a
        // winding mistake obvious instead of silently hiding an entire mesh.
        chunkMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
    }

    public Node getNode() { return node; }
    public int getBlockCount() { return blocks.size(); }
    public List<WorldCreature> getCreatures() { return List.copyOf(creatures); }
    public int getCreatureCount() { return creatures.size(); }

    public boolean addCreature(WorldCreature creature) {
        Objects.requireNonNull(creature, "creature");
        if (creatures.size() >= 256 || creatures.stream().anyMatch(existing -> existing.id() == creature.id())) return false;
        creatures.add(creature);
        return true;
    }

    public void replaceCreatureSnapshot(List<WorldCreature> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.size() > 256) throw new IllegalArgumentException("Creature snapshot exceeds limit");
        creatures.clear();
        creatures.addAll(snapshot);
    }

    public boolean updateCreature(WorldCreature updated) {
        Objects.requireNonNull(updated, "updated");
        for (int i = 0; i < creatures.size(); i++) {
            if (creatures.get(i).id() == updated.id()) {
                creatures.set(i, updated);
                return true;
            }
        }
        return false;
    }

    public boolean removeCreature(long id) {
        return creatures.removeIf(creature -> creature.id() == id);
    }
    public int getChunkCount() { return chunkBlocks.size(); }
    public int getRenderedChunkCount() { return chunkGeometries.size(); }
    public int getPendingChunkCount() { return pendingChunks.size(); }
    public int getRenderDistanceChunks() { return renderDistanceChunks; }
    public long getSeed() { return seed; }
    public int getWorldRadius() { return worldRadius; }
    public boolean hasActiveBiomes() { return activeBiomes; }
    public boolean hasExtendedBiomes() { return extendedBiomes; }
    public boolean hasCaveBiomes() { return caveBiomesEnabled; }
    public boolean hasEnchantedBiomes() { return enchantedBiomeEnabled; }
    public CaveBiomeType getCaveBiomeAt(int x, int y, int z) {
        return caveBiomesEnabled ? CaveBiomeGenerator.biomeAt(seed, x, y, z) : CaveBiomeType.NONE;
    }
    public Map<BlockPos, BlockType> snapshotBlocks() { return Map.copyOf(blocks); }

    /** Visits stored blocks without allocating a full snapshot copy. */
    public void forEachBlock(BiConsumer<BlockPos, BlockType> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        blocks.forEach(visitor);
    }

    /** Replace block data with an authoritative multiplayer snapshot. Call on the render thread. */
    public void replaceBlockSnapshot(long worldSeed, boolean hasBiomes, boolean hasExtendedBiomes,
                                     int snapshotRadius, Map<BlockPos, BlockType> snapshot) {
        replaceBlockSnapshot(worldSeed, hasBiomes, hasExtendedBiomes, false, snapshotRadius, snapshot);
    }

    public void replaceBlockSnapshot(long worldSeed, boolean hasBiomes, boolean hasExtendedBiomes,
                                     boolean hasEnchantedBiomes, int snapshotRadius,
                                     Map<BlockPos, BlockType> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.size() > MAX_SAVED_BLOCKS) throw new IllegalArgumentException("World snapshot exceeds block limit");
        node.detachAllChildren();
        chunkGeometries.clear();
        triangleHits.clear();
        pendingChunks.clear();
        desiredChunks.clear();
        chunkBlocks.clear();
        blocks.clear();
        creatures.clear();
        preservedUnknownIds.clear();
        columnHeights.clear();
        hasBounds = false;
        lastStreamCenter = null;
        lastMeshError = "";
        seed = worldSeed;
        activeBiomes = hasBiomes;
        extendedBiomes = hasExtendedBiomes;
        enchantedBiomeEnabled = hasEnchantedBiomes;
        caveBiomesEnabled = snapshot.values().stream().anyMatch(type -> type == BlockType.GLOWING_CRYSTAL
                || type == BlockType.CAVE_MOSS || type == BlockType.CAVE_FERN);
        worldRadius = Math.max(0, Math.min(100_000, snapshotRadius));
        bulkUpdate = true;
        try {
            for (Map.Entry<BlockPos, BlockType> entry : snapshot.entrySet()) {
                addBlock(entry.getKey(), entry.getValue());
            }
        } finally {
            bulkUpdate = false;
        }
        landmarks = activeBiomes ? WorldNavigation.allMarkers(seed, worldRadius) : List.of();
    }
    public long countBlocks(BlockType type) {
        Objects.requireNonNull(type, "type");
        return blocks.values().stream().filter(type::equals).count();
    }
    public List<WorldNavigation.Landmark> getLandmarks() { return landmarks; }

    public BiomeType getBiomeAt(int x, int z) {
        if (!activeBiomes) return BiomeType.PLAINS;
        if (enchantedBiomeEnabled) return BiomeGenerator.biomeAtV199(seed, x, z);
        return extendedBiomes ? BiomeGenerator.biomeAtV110(seed, x, z) : BiomeGenerator.biomeAt(seed, x, z);
    }
    public int getMinBlockX() { return hasBounds ? minBlockX : 0; }
    public int getMaxBlockX() { return hasBounds ? maxBlockX : 0; }
    public int getMinBlockZ() { return hasBounds ? minBlockZ : 0; }
    public int getMaxBlockZ() { return hasBounds ? maxBlockZ : 0; }
    public String getLastMeshError() { return lastMeshError; }
    public boolean isTwoSidedRendering() { return twoSidedRendering; }

    /** Toggle GPU back-face culling; useful on machines with different drivers. */
    public void setTwoSidedRendering(boolean enabled) {
        twoSidedRendering = enabled;
        chunkMaterial.getAdditionalRenderState().setFaceCullMode(
                enabled ? RenderState.FaceCullMode.Off : RenderState.FaceCullMode.Back);
    }

    /** Selects a nearby, open grass column so the player never starts inside a tree canopy. */
    public Vector3f getSuggestedSpawn() {
        if (columnHeights.isEmpty()) return new Vector3f(0f, 4.68f, 8f);
        long bestKey = 0L;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Map.Entry<Long, Integer> entry : columnHeights.entrySet()) {
            long key = entry.getKey();
            int x = (int) (key >> 32);
            int z = (int) key;
            int y = entry.getValue();
            BlockType surface = blocks.get(new BlockPos(x, y, z));
            boolean openAbove = !blocks.containsKey(new BlockPos(x, y + 1, z))
                    && !blocks.containsKey(new BlockPos(x, y + 2, z));
            boolean naturalSurface = surface == BlockType.GRASS || surface == BlockType.SAND;
            double dx = x;
            double dz = z - 8.0;
            double score = dx * dx + dz * dz;
            if (!naturalSurface) score += 100_000.0;
            if (!openAbove) score += 1_000_000.0;
            if (score < bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }
        int x = (int) (bestKey >> 32);
        int z = (int) bestKey;
        int y = columnHeights.get(bestKey);
        return new Vector3f(x, y + 1.68f, z);
    }

    public void setRenderDistanceChunks(int distance) {
        renderDistanceChunks = Math.max(2, Math.min(MAX_RENDER_DISTANCE, distance));
        lastStreamCenter = null;
    }

    public boolean contains(BlockPos pos) { return blocks.containsKey(pos); }
    public BlockType getBlock(BlockPos pos) { return blocks.get(pos); }

    /** Returns the highest solid block in a column, or -13 below the world floor. */
    public int highestBlockY(int x, int z) {
        return columnHeights.getOrDefault(columnKey(x, z), -13);
    }

    /** Finds the highest block below a ceiling; used by creatures that walk inside cave rooms. */
    public int highestBlockYAtOrBelow(int x, int z, int ceilingY) {
        int highest = -13;
        Set<BlockPos> members = chunkBlocks.get(ChunkPos.fromBlock(x, z));
        if (members == null) return highest;
        for (BlockPos pos : members) {
            if (pos.x() == x && pos.z() == z && pos.y() <= ceilingY && pos.y() > highest) {
                highest = pos.y();
            }
        }
        return highest;
    }

    public boolean isChunkRenderedAt(float worldX, float worldZ) {
        return chunkGeometries.containsKey(ChunkPos.fromBlock(Math.round(worldX), Math.round(worldZ)));
    }

    /** Resolve the block/face from a triangle in one of the combined chunk meshes. */
    public BlockHit getHitFace(Geometry geometry, int triangleIndex) {
        List<BlockHit> hits = triangleHits.get(geometry);
        if (hits == null || triangleIndex < 0) return null;
        int faceIndex = triangleIndex / 2;
        return faceIndex < hits.size() ? hits.get(faceIndex) : null;
    }

    public boolean addBlock(BlockPos pos, BlockType type) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(type, "type");
        if (blocks.size() >= MAX_SAVED_BLOCKS || blocks.containsKey(pos)) return false;
        blocks.put(pos, type);
        if (!hasBounds) {
            minBlockX = maxBlockX = pos.x();
            minBlockZ = maxBlockZ = pos.z();
            hasBounds = true;
        } else {
            minBlockX = Math.min(minBlockX, pos.x());
            maxBlockX = Math.max(maxBlockX, pos.x());
            minBlockZ = Math.min(minBlockZ, pos.z());
            maxBlockZ = Math.max(maxBlockZ, pos.z());
        }
        ChunkPos chunk = ChunkPos.fromBlock(pos.x(), pos.z());
        chunkBlocks.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(pos);
        columnHeights.merge(columnKey(pos.x(), pos.z()), pos.y(), Math::max);
        if (!bulkUpdate) markBlockAndBorderChunksDirty(pos);
        return true;
    }

    public boolean removeBlock(BlockPos pos) {
        BlockType removed = blocks.remove(pos);
        if (removed == null) return false;
        preservedUnknownIds.remove(pos);
        ChunkPos chunk = ChunkPos.fromBlock(pos.x(), pos.z());
        Set<BlockPos> members = chunkBlocks.get(chunk);
        if (members != null) {
            members.remove(pos);
            if (members.isEmpty()) chunkBlocks.remove(chunk);
        }
        if (columnHeights.getOrDefault(columnKey(pos.x(), pos.z()), -13) == pos.y()) {
            updateColumnHeightAfterRemoval(pos.x(), pos.z());
        }
        if (!bulkUpdate) markBlockAndBorderChunksDirty(pos);
        return true;
    }

    /** Call once per frame on the render thread; only a few pending chunks are meshed per frame. */
    public void updateStreaming(Vector3f cameraPosition) {
        updateStreaming(cameraPosition, 3);
    }

    public void updateStreaming(Vector3f cameraPosition, int maxChunkBuilds) {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        ChunkPos center = ChunkPos.fromBlock(Math.round(cameraPosition.x), Math.round(cameraPosition.z));
        if (!center.equals(lastStreamCenter)) {
            lastStreamCenter = center;
            Set<ChunkPos> previousDesired = new HashSet<>(desiredChunks);
            desiredChunks.clear();
            List<ChunkPos> near = new ArrayList<>();
            for (int dx = -renderDistanceChunks; dx <= renderDistanceChunks; dx++) {
                for (int dz = -renderDistanceChunks; dz <= renderDistanceChunks; dz++) {
                    ChunkPos candidate = new ChunkPos(center.x() + dx, center.z() + dz);
                    if (dx * dx + dz * dz <= renderDistanceChunks * renderDistanceChunks
                            && chunkBlocks.containsKey(candidate)) {
                        near.add(candidate);
                        desiredChunks.add(candidate);
                    }
                }
            }
            near.sort(Comparator.comparingInt((ChunkPos c) -> distanceSquared(c, center))
                    .thenComparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z));

            for (ChunkPos existing : new ArrayList<>(chunkGeometries.keySet())) {
                if (!desiredChunks.contains(existing)) detachChunk(existing);
            }
            pendingChunks.removeIf(chunk -> !desiredChunks.contains(chunk));
            for (ChunkPos chunk : near) {
                if (!chunkGeometries.containsKey(chunk)) {
                    pendingChunks.add(chunk);
                } else if (chunkBorderVisibilityChanged(chunk, previousDesired)) {
                    detachChunk(chunk);
                    pendingChunks.add(chunk);
                }
            }
        }

        int built = 0;
        int budget = Math.max(1, maxChunkBuilds);
        while (built < budget && !pendingChunks.isEmpty()) {
            ChunkPos chunk = pendingChunks.iterator().next();
            pendingChunks.remove(chunk);
            if (desiredChunks.contains(chunk) && chunkBlocks.containsKey(chunk)) {
                try {
                    rebuildChunk(chunk);
                } catch (RuntimeException ex) {
                    lastMeshError = ex.getClass().getSimpleName() + ": "
                            + (ex.getMessage() == null ? "без описания" : ex.getMessage());
                    throw ex;
                }
                built++;
            }
        }
    }

    private int distanceSquared(ChunkPos a, ChunkPos b) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    private boolean chunkBorderVisibilityChanged(ChunkPos chunk, Set<ChunkPos> previousDesired) {
        ChunkPos[] neighbors = {
                new ChunkPos(chunk.x() - 1, chunk.z()),
                new ChunkPos(chunk.x() + 1, chunk.z()),
                new ChunkPos(chunk.x(), chunk.z() - 1),
                new ChunkPos(chunk.x(), chunk.z() + 1)
        };
        for (ChunkPos neighbor : neighbors) {
            if (previousDesired.contains(neighbor) != desiredChunks.contains(neighbor)) return true;
        }
        return false;
    }

    private void markBlockAndBorderChunksDirty(BlockPos pos) {
        ChunkPos own = ChunkPos.fromBlock(pos.x(), pos.z());
        markChunkDirty(own);
        int localX = Math.floorMod(pos.x(), ChunkPos.SIZE);
        int localZ = Math.floorMod(pos.z(), ChunkPos.SIZE);
        if (localX == 0) markChunkDirty(new ChunkPos(own.x() - 1, own.z()));
        if (localX == ChunkPos.SIZE - 1) markChunkDirty(new ChunkPos(own.x() + 1, own.z()));
        if (localZ == 0) markChunkDirty(new ChunkPos(own.x(), own.z() - 1));
        if (localZ == ChunkPos.SIZE - 1) markChunkDirty(new ChunkPos(own.x(), own.z() + 1));
    }

    private void markChunkDirty(ChunkPos chunk) {
        if (!desiredChunks.contains(chunk)) return;
        detachChunk(chunk);
        pendingChunks.add(chunk);
    }

    private void detachChunk(ChunkPos chunk) {
        Geometry geometry = chunkGeometries.remove(chunk);
        if (geometry != null) geometry.removeFromParent();
        if (geometry != null) triangleHits.remove(geometry);
    }

    private void rebuildChunk(ChunkPos chunk) {
        detachChunk(chunk);
        Set<BlockPos> members = chunkBlocks.get(chunk);
        if (members == null || members.isEmpty()) return;

        List<Float> positions = new ArrayList<>();
        List<Float> colors = new ArrayList<>();
        List<Float> texCoords = texturedChunks ? new ArrayList<>() : null;
        List<Integer> indices = new ArrayList<>();
        List<BlockHit> hits = new ArrayList<>();
        int baseX = chunk.minBlockX();
        int baseZ = chunk.minBlockZ();

        for (BlockPos pos : members) {
            BlockType type = blocks.get(pos);
            if (type == null) continue;
            ColorRGBA base = blockPalette.getOrDefault(type, type.color());
            int localX = pos.x() - baseX;
            int localZ = pos.z() - baseZ;
            for (Face face : FACES) {
                BlockPos neighbor = pos.add(face.nx, face.ny, face.nz);
                if (blocks.containsKey(neighbor)) {
                    boolean neighborChunkDesired = true;
                    if (face.nx != 0 && ((face.nx > 0 && localX == ChunkPos.SIZE - 1)
                            || (face.nx < 0 && localX == 0))) {
                        neighborChunkDesired = desiredChunks.contains(new ChunkPos(chunk.x() + face.nx, chunk.z()));
                    } else if (face.nz != 0 && ((face.nz > 0 && localZ == ChunkPos.SIZE - 1)
                            || (face.nz < 0 && localZ == 0))) {
                        neighborChunkDesired = desiredChunks.contains(new ChunkPos(chunk.x(), chunk.z() + face.nz));
                    }
                    if (neighborChunkDesired) continue;
                }
                int firstVertex = positions.size() / 3;
                int faceVariant = face.ny > 0 ? 0 : face.ny < 0 ? 2 : 1;
                int tileIndex = ResourcePackManager.textureTileIndex(type, faceVariant);
                int tileX = tileIndex % ResourcePackManager.ATLAS_COLUMNS;
                int tileY = tileIndex / ResourcePackManager.ATLAS_COLUMNS;
                float atlasSize = ResourcePackManager.ATLAS_SIZE;
                float inset = 0.5f / atlasSize;
                float u0 = tileX / (float) ResourcePackManager.ATLAS_COLUMNS + inset;
                float u1 = (tileX + 1) / (float) ResourcePackManager.ATLAS_COLUMNS - inset;
                float vTop = 1f - tileY / (float) ResourcePackManager.ATLAS_COLUMNS - inset;
                float vBottom = 1f - (tileY + 1) / (float) ResourcePackManager.ATLAS_COLUMNS + inset;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * 3;
                    positions.add(localX + face.vertices[offset]);
                    positions.add(pos.y() + face.vertices[offset + 1]);
                    positions.add(localZ + face.vertices[offset + 2]);
                    if (texturedChunks) {
                        // Texture pixels already contain the pack's base color; vertex color only shades each face.
                        colors.add(clamp(face.shade));
                        colors.add(clamp(face.shade));
                        colors.add(clamp(face.shade));
                        colors.add(1f);
                        texCoords.add(QUAD_UV_CORNERS[vertex][0] == 0 ? u0 : u1);
                        texCoords.add(QUAD_UV_CORNERS[vertex][1] == 0 ? vTop : vBottom);
                    } else {
                        // Compatibility path used by older tests and callers without a texture atlas.
                        colors.add(clamp(base.r * face.shade));
                        colors.add(clamp(base.g * face.shade));
                        colors.add(clamp(base.b * face.shade));
                        colors.add(1f);
                    }
                }
                indices.add(firstVertex);
                indices.add(firstVertex + 1);
                indices.add(firstVertex + 2);
                indices.add(firstVertex);
                indices.add(firstVertex + 2);
                indices.add(firstVertex + 3);
                // One entry per quad; jME reports two triangles for each face.
                hits.add(new BlockHit(pos, face.nx, face.ny, face.nz));
            }
        }

        if (indices.isEmpty()) return;
        float[] positionArray = toFloatArray(positions);
        float[] colorArray = toFloatArray(colors);
        int[] indexArray = indices.stream().mapToInt(Integer::intValue).toArray();
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Triangles);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positionArray));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colorArray));
        if (texturedChunks) mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(toFloatArray(texCoords)));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indexArray));
        mesh.updateBound();
        mesh.setStatic();

        Geometry geometry = new Geometry("Chunk[" + chunk.x() + "," + chunk.z() + "]", mesh);
        geometry.setMaterial(chunkMaterial);
        geometry.setLocalTranslation(baseX, 0f, baseZ);
        geometry.setQueueBucket(RenderQueue.Bucket.Opaque);
        geometry.setShadowMode(RenderQueue.ShadowMode.Off);
        node.attachChild(geometry);
        chunkGeometries.put(chunk, geometry);
        triangleHits.put(geometry, hits);
    }

    private float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private void updateColumnHeightAfterRemoval(int x, int z) {
        int highest = -13;
        // Only this 16x16 chunk can contain blocks from the requested column.
        // Scanning the entire world here made river/structure generation quadratic.
        Set<BlockPos> members = chunkBlocks.get(ChunkPos.fromBlock(x, z));
        if (members != null) {
            for (BlockPos pos : members) {
                if (pos.x() == x && pos.z() == z && pos.y() > highest) {
                    highest = pos.y();
                }
            }
        }
        long key = columnKey(x, z);
        if (highest == -13) columnHeights.remove(key);
        else columnHeights.put(key, highest);
    }

    public void generate(GameVersion version, long worldSeed) {
        Objects.requireNonNull(version, "version");
        generate(version, worldSeed, version.worldRadius());
    }

    /** Generates a world using a radius selected in the launcher. */
    public void generate(GameVersion version, long worldSeed, int requestedRadius) {
        Objects.requireNonNull(version, "version");
        if (requestedRadius < 1 || requestedRadius > 512) {
            throw new IllegalArgumentException("World radius must be between 1 and 512 blocks.");
        }
        clear();
        seed = worldSeed;
        worldRadius = requestedRadius;
        activeBiomes = supportsBiomes(version.id());
        extendedBiomes = supportsExtendedBiomes(version.id());
        enchantedBiomeEnabled = BiomeGenerator.supportsEnchantedGrove(version.id());
        caveBiomesEnabled = CaveBiomeGenerator.supports(version.id());
        bulkUpdate = true;
        try {
            Random random = new Random(seed);
            int radius = requestedRadius;
            int minY = version.caves() ? -12 : -2;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int surface = terrainHeightFor(x, z);
                    // Biome is constant for a vertical column; calculate it once, not per block.
                    BiomeType biome = getBiomeAt(x, z);
                    for (int y = minY; y <= surface; y++) {
                        if (version.caves() && y < surface - 2 && y > minY + 1 && isCave(x, y, z)) continue;
                        BlockType type;
                        if (y == surface) type = biome.surfaceBlock();
                        else if (y >= surface - 2) type = biome == BiomeType.DESERT ? BlockType.SANDSTONE : BlockType.DIRT;
                        else {
                            type = BlockType.STONE;
                            if (version.ores()) type = supportsDeepCaves(version.id()) ? oreForDeepCaves195(x, y, z)
                                    : supportsRelease100(version.id()) ? oreForRelease100(x, y, z) : oreFor(x, y, z);
                        }
                        addBlock(new BlockPos(x, y, z), type);
                    }
                }
            }
            if (version.trees()) {
                for (int x = -radius + 3; x <= radius - 3; x += 4) {
                    for (int z = -radius + 3; z <= radius - 3; z += 4) {
                        BiomeType biome = getBiomeAt(x, z);
                        float density = switch (biome) {
                            case FOREST -> 0.62f;
                            case TAIGA -> 0.48f;
                            case SWAMP -> 0.34f;
                            case ENCHANTED_GROVE -> 0.70f;
                            case PLAINS -> 0.22f;
                            default -> 0f;
                        };
                        if (random.nextFloat() < density && (Math.abs(x) > 3 || Math.abs(z) > 3)) addTree(x, z);
                    }
                }
            }
            if (enchantedBiomeEnabled) addEnchantedGroveDecorations(random, radius);
            if (version.id().compareTo("0.6.0") >= 0) {
                // A winding surface river; water blocks are saved like every other block.
                for (int x = -radius + 2; x <= radius - 2; x++) {
                    int centerZ = (int) Math.round(Math.sin(x * 0.075) * 7.0);
                    for (int dz = -1; dz <= 1; dz++) {
                        int z = centerZ + dz;
                        int surface = terrainHeightFor(x, z);
                        BlockPos riverBed = new BlockPos(x, surface, z);
                        removeBlock(riverBed);
                        addBlock(riverBed, BlockType.WATER);
                        if (surface <= 1) addBlock(new BlockPos(x, surface + 1, z), BlockType.WATER);
                    }
                }
                // Small underground lava pockets, safely away from the spawn area.
                for (int x = -radius + 4; x <= radius - 4; x += 5) {
                    for (int z = -radius + 4; z <= radius - 4; z += 5) {
                        if (Math.abs(x) + Math.abs(z) > 15 && random.nextFloat() < 0.12f) {
                            int y = -7 - random.nextInt(3);
                            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++)
                                addBlock(new BlockPos(x + dx, y, z + dz), BlockType.LAVA);
                        }
                    }
                }
            }
            if (supportsExtendedBiomes(version.id())) {
                addCrystalCaves(random, radius);
            }
            if (supportsDeepCaves(version.id())) {
                addCrystalCaverns(random, radius);
            }
            if (caveBiomesEnabled) {
                addSubterraneanBiomes(random, radius, supportsCrystalBosses(version.id()));
            }
            if (supportsAncientTemples(version.id())) {
                addAncientTemples(radius);
            }
            if (version.id().compareTo("0.8.0") >= 0) {
                addVillageHouse(-34, 27);
                addVillageHouse(29, 27);
                if (supportsExtendedBiomes(version.id())) {
                    addVillageMarketStall(-34, 27);
                    addVillageGarden(29, 27);
                }
            }
            if (WorldNavigation.supportsExploration(version.id())) {
                landmarks = WorldNavigation.allMarkers(seed, radius);
                for (WorldNavigation.Landmark site : WorldNavigation.generateSites(seed, radius)) {
                    switch (site.type()) {
                        case WATCHTOWER -> addWatchtower(site.x(), site.z());
                        case RUINS -> addRuins(site.x(), site.z());
                        case CAMP -> addExplorationCamp(site.x(), site.z());
                        case MINESHAFT -> addMineEntrance(site.x(), site.z());
                        case VILLAGE -> { }
                    }
                }
            }
            if (version.id().compareTo("0.8.0") >= 0) {
                spawnCreatures(random, radius);
            }
        } finally {
            bulkUpdate = false;
        }
        lastStreamCenter = null;
    }

    private void addWatchtower(int x, int z) {
        int groundY = terrainHeightFor(x, z);
        for (int level = 1; level <= 7; level++) {
            for (int dx : new int[]{-2, 2}) {
                for (int dz : new int[]{-2, 2}) {
                    addBlock(new BlockPos(x + dx, groundY + level, z + dz), BlockType.WOOD);
                }
            }
            if (level == 3 || level == 7) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        addBlock(new BlockPos(x + dx, groundY + level, z + dz), BlockType.WOOD);
                    }
                }
            }
        }
        addBlock(new BlockPos(x, groundY + 4, z), BlockType.TREASURE_CHEST);
    }

    private void addRuins(int x, int z) {
        int groundY = terrainHeightFor(x, z);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                if (!edge || (dx + dz) % 3 == 0) continue;
                int height = 1 + Math.floorMod(dx * 7 + dz * 11, 3);
                for (int dy = 1; dy <= height; dy++) {
                    addBlock(new BlockPos(x + dx, groundY + dy, z + dz), BlockType.STONE);
                }
            }
        }
        addBlock(new BlockPos(x, groundY + 1, z), BlockType.TREASURE_CHEST);
    }

    private void addExplorationCamp(int x, int z) {
        int groundY = terrainHeightFor(x, z);
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                addBlock(new BlockPos(x + dx, groundY + 1, z + dz), BlockType.WOOD);
                addBlock(new BlockPos(x + dx, groundY + 2, z + dz), BlockType.WOOD);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            addBlock(new BlockPos(x + dx, groundY + 3, z), BlockType.WOOD);
        }
        addBlock(new BlockPos(x, groundY + 1, z), BlockType.FURNACE);
        addBlock(new BlockPos(x + 1, groundY + 1, z), BlockType.TREASURE_CHEST);
    }

    private void addMineEntrance(int x, int z) {
        int groundY = terrainHeightFor(x, z);
        for (int step = 0; step <= 8; step++) {
            int tunnelX = x + step;
            int floorY = groundY - step;
            removeBlock(new BlockPos(tunnelX, floorY + 1, z));
            removeBlock(new BlockPos(tunnelX, floorY + 2, z));
            removeBlock(new BlockPos(tunnelX, floorY + 3, z));
            if (!blocks.containsKey(new BlockPos(tunnelX, floorY, z))) {
                addBlock(new BlockPos(tunnelX, floorY, z), BlockType.STONE);
            }
            if (step % 2 == 0) {
                addBlock(new BlockPos(tunnelX, floorY + 2, z - 1), BlockType.WOOD);
                addBlock(new BlockPos(tunnelX, floorY + 2, z + 1), BlockType.WOOD);
                addBlock(new BlockPos(tunnelX, floorY + 3, z), BlockType.WOOD);
            }
        }
        addBlock(new BlockPos(x + 8, groundY - 7, z), BlockType.TREASURE_CHEST);
    }

    private void addCrystalCaves(Random random, int radius) {
        int created = 0;
        for (int centerX = -radius + 14; centerX <= radius - 14 && created < 8; centerX += 23) {
            for (int centerZ = -radius + 14; centerZ <= radius - 14 && created < 8; centerZ += 23) {
                if (Math.abs(centerX) + Math.abs(centerZ) < 48 || random.nextFloat() > 0.24f) continue;
                createCrystalLake(centerX, -8 - random.nextInt(2), centerZ);
                created++;
            }
        }
        if (created == 0) createCrystalLake(radius - 22, -8, -radius + 22);
    }

    private void createCrystalLake(int centerX, int centerY, int centerZ) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double shape = (dx * dx + dz * dz) / 9.0 + (dy * dy) / 4.0;
                    if (shape > 1.35) continue;
                    BlockPos position = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                    if (shape < 0.76) {
                        removeBlock(position);
                        if (dy <= -1 && shape < 0.42) addBlock(position, BlockType.WATER);
                    } else if (shape <= 1.35 && blocks.containsKey(position)
                            && coordinateRandom(position.x(), position.y(), position.z()) < 0.42f) {
                        removeBlock(position);
                        addBlock(position, BlockType.AMETHYST_ORE);
                    }
                }
            }
        }
    }

    /** Adds larger, seed-stable caverns with crystal-lined walls to the 1.9.5 profile. */
    private void addCrystalCaverns(Random random, int radius) {
        int created = 0;
        for (int centerX = -radius + 22; centerX <= radius - 22; centerX += 44) {
            for (int centerZ = -radius + 22; centerZ <= radius - 22; centerZ += 44) {
                if (Math.abs(centerX) + Math.abs(centerZ) < 56 || random.nextFloat() > 0.22f) continue;
                createCrystalCavern(centerX, -6 - random.nextInt(5), centerZ, random);
                created++;
            }
        }
        int[][] fallback = {{radius - 24, -radius + 24}, {-radius + 24, radius - 24},
                {radius - 24, radius - 24}, {-radius + 24, -radius + 24}};
        for (int[] center : fallback) {
            if (created >= 3) break;
            if (Math.abs(center[0]) + Math.abs(center[1]) < 48) continue;
            createCrystalCavern(center[0], -8, center[1], random);
            created++;
        }
    }

    private void createCrystalCavern(int centerX, int centerY, int centerZ, Random random) {
        int radiusX = 3 + random.nextInt(3);
        int radiusY = 2 + random.nextInt(2);
        int radiusZ = 3 + random.nextInt(3);
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    double shape = (dx * dx) / (double) (radiusX * radiusX)
                            + (dy * dy) / (double) (radiusY * radiusY)
                            + (dz * dz) / (double) (radiusZ * radiusZ);
                    if (shape > 1.12) continue;
                    BlockPos position = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                    if (shape < 0.72) {
                        removeBlock(position);
                        if (dy <= -1 && shape < 0.34 && coordinateRandom(position.x(), position.y(), position.z()) < 0.14f) {
                            addBlock(position, BlockType.WATER);
                        }
                    } else if (blocks.get(position) == BlockType.STONE) {
                        float mineral = coordinateRandom(position.x(), position.y(), position.z());
                        BlockType deposit = mineral < 0.10f ? BlockType.AMETHYST_ORE
                                : mineral < 0.14f ? BlockType.RUBY_ORE
                                : mineral < 0.20f ? BlockType.SAPPHIRE_ORE : null;
                        if (deposit != null) {
                            removeBlock(position);
                            addBlock(position, deposit);
                        }
                    }
                }
            }
        }
    }

    /** Places seed-selected underground biome rooms without changing older world profiles. */
    private void addSubterraneanBiomes(Random random, int radius, boolean includeCrystalStructures) {
        int cellSize = CaveBiomeGenerator.cellSize();
        int minCell = Math.floorDiv(-radius, cellSize);
        int maxCell = Math.floorDiv(radius, cellSize);
        boolean bossSpawned = false;
        for (int cellX = minCell; cellX <= maxCell; cellX++) {
            int centerX = cellX * cellSize + cellSize / 2;
            if (Math.abs(centerX) > radius - 9) continue;
            for (int cellZ = minCell; cellZ <= maxCell; cellZ++) {
                int centerZ = cellZ * cellSize + cellSize / 2;
                if (Math.abs(centerZ) > radius - 9 || Math.abs(centerX) + Math.abs(centerZ) < 42) continue;
                int centerY = -6 - random.nextInt(6);
                CaveBiomeType biome = CaveBiomeGenerator.biomeAt(seed, centerX, centerY, centerZ);
                if (biome == CaveBiomeType.NONE) continue;
                int floorY = createSubterraneanRoom(centerX, centerY, centerZ, biome, random, includeCrystalStructures);
                if (includeCrystalStructures && biome == CaveBiomeType.CRYSTAL_GROTTO && !bossSpawned) {
                    long bossId = 1_000_000L + Math.floorMod((long) centerX * 31L + centerZ, 1_000_000L);
                    addCreature(new WorldCreature(bossId, CreatureType.CRYSTAL_SENTINEL,
                            centerX + 0.5f, floorY + 1f, centerZ + 0.5f, 0f));
                    bossSpawned = true;
                }
            }
        }
    }

    private int createSubterraneanRoom(int centerX, int centerY, int centerZ,
                                       CaveBiomeType biome, Random random, boolean includeCrystalStructures) {
        int radiusX = biome == CaveBiomeType.UNDERGROUND_LAKE ? 5 + random.nextInt(3) : 4 + random.nextInt(2);
        int radiusY = 2 + random.nextInt(2);
        int radiusZ = biome == CaveBiomeType.UNDERGROUND_LAKE ? 5 + random.nextInt(3) : 4 + random.nextInt(2);
        List<BlockPos> fernCandidates = new ArrayList<>();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    double shape = (dx * dx) / (double) (radiusX * radiusX)
                            + (dy * dy) / (double) (radiusY * radiusY)
                            + (dz * dz) / (double) (radiusZ * radiusZ);
                    if (shape > 1.12) continue;
                    BlockPos position = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                    if (shape < 0.72) {
                        removeBlock(position);
                        if (dy == -radiusY + 1) {
                            float detail = coordinateRandom(position.x(), position.y(), position.z());
                            if (biome == CaveBiomeType.UNDERGROUND_LAKE
                                    && Math.abs(dx) <= radiusX / 2 && Math.abs(dz) <= radiusZ / 2) {
                                addBlock(position, BlockType.WATER);
                            } else if (biome == CaveBiomeType.CRYSTAL_GROTTO && shape < 0.26 && detail < 0.28f) {
                                addBlock(position, BlockType.WATER);
                            } else if (biome == CaveBiomeType.MOSS_CAVERN && shape < 0.82) {
                                if (detail < 0.13f) {
                                    addBlock(position, BlockType.WATER);
                                } else {
                                    addBlock(position, BlockType.CAVE_MOSS);
                                    if (detail > 0.68f && !blocks.containsKey(position.add(0, 1, 0))) {
                                        fernCandidates.add(position.add(0, 1, 0));
                                    }
                                }
                            }
                        }
                    } else if (blocks.get(position) == BlockType.STONE) {
                        float mineral = coordinateRandom(position.x(), position.y(), position.z());
                        BlockType decoration = null;
                        if (biome == CaveBiomeType.CRYSTAL_GROTTO) {
                            if (mineral < 0.24f) decoration = BlockType.GLOWING_CRYSTAL;
                            else if (mineral < 0.34f) decoration = BlockType.AMETHYST_ORE;
                        } else if (biome == CaveBiomeType.MOSS_CAVERN && mineral < 0.10f) {
                            decoration = BlockType.CAVE_MOSS;
                        } else if (biome == CaveBiomeType.UNDERGROUND_LAKE && mineral < 0.06f) {
                            decoration = BlockType.GLOWING_CRYSTAL;
                        }
                        if (decoration != null) {
                            removeBlock(position);
                            addBlock(position, decoration);
                        }
                    }
                }
            }
        }
        // Place sprouts after carving so later carve cells cannot erase them.
        for (BlockPos fern : fernCandidates) {
            if (!blocks.containsKey(fern)) addBlock(fern, BlockType.CAVE_FERN);
        }
        int floorY = Math.max(-12, centerY - radiusY);
        if (includeCrystalStructures && biome == CaveBiomeType.CRYSTAL_GROTTO) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos floor = new BlockPos(centerX + dx, floorY, centerZ + dz);
                    removeBlock(floor);
                    addBlock(floor, BlockType.STONE);
                }
            }
            addCrystalTree(centerX + 2, floorY + 1, centerZ + 1);
            BlockPos cache = new BlockPos(centerX - 2, floorY + 1, centerZ - 1);
            removeBlock(cache);
            addBlock(cache, BlockType.CRYSTAL_CACHE);
        }
        return floorY;
    }

    /** A small bioluminescent tree made from the existing stable block IDs. */
    private void addCrystalTree(int x, int baseY, int z) {
        for (int dy = 0; dy < 2; dy++) {
            BlockPos trunk = new BlockPos(x, baseY + dy, z);
            removeBlock(trunk);
            addBlock(trunk, BlockType.GLOWING_CRYSTAL);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                BlockPos crown = new BlockPos(x + dx, baseY + 2, z + dz);
                removeBlock(crown);
                addBlock(crown, dx == 0 && dz == 0 ? BlockType.AMETHYST_ORE : BlockType.CAVE_FERN);
            }
        }
        for (int dx : new int[]{-1, 1}) {
            BlockPos branch = new BlockPos(x + dx, baseY + 1, z);
            removeBlock(branch);
            addBlock(branch, BlockType.GLOWING_CRYSTAL);
        }
    }

    /** Adds sparse crystal and fern markers to the surface of the enchanted grove. */
    private void addEnchantedGroveDecorations(Random random, int radius) {
        int placed = 0;
        for (int x = -radius + 4; x <= radius - 4; x += 5) {
            for (int z = -radius + 4; z <= radius - 4; z += 5) {
                if (getBiomeAt(x, z) != BiomeType.ENCHANTED_GROVE || random.nextFloat() >= 0.18f) continue;
                int surfaceY = highestBlockY(x, z);
                if (getBlock(new BlockPos(x, surfaceY, z)) != BlockType.CAVE_MOSS) continue;
                BlockPos decoration = new BlockPos(x, surfaceY + 1, z);
                if (!contains(decoration)) {
                    addBlock(decoration, random.nextBoolean() ? BlockType.GLOWING_CRYSTAL : BlockType.CAVE_FERN);
                    placed++;
                }
            }
        }
        if (placed == 0) {
            search:
            for (int x = -radius + 2; x <= radius - 2; x++) {
                for (int z = -radius + 2; z <= radius - 2; z++) {
                    if (getBiomeAt(x, z) == BiomeType.ENCHANTED_GROVE) {
                        int y = highestBlockY(x, z);
                        if (getBlock(new BlockPos(x, y, z)) == BlockType.CAVE_MOSS
                                && !contains(new BlockPos(x, y + 1, z))) {
                            addBlock(new BlockPos(x, y + 1, z), BlockType.GLOWING_CRYSTAL);
                            break search;
                        }
                    }
                }
            }
        }
    }

    /** Creates two remote sandstone temples with artifact caches and their own wardens. */
    private void addAncientTemples(int radius) {
        // Keep both footprints inside the circular playable terrain even at smaller sizes.
        int offset = Math.max(8, radius / 2 - 2);
        addAncientTemple(offset, -offset, 2_100_001L);
        addAncientTemple(-offset, offset, 2_100_002L);
    }

    private void addAncientTemple(int centerX, int centerZ, long guardianId) {
        int baseY = Integer.MIN_VALUE;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                baseY = Math.max(baseY, highestBlockY(centerX + dx, centerZ + dz));
            }
        }
        if (baseY < -12) return;

        // Level the footprint with sandstone and clear trees/terrain above the platform.
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int localTop = highestBlockY(x, z);
                for (int y = localTop + 1; y < baseY; y++) {
                    BlockPos fill = new BlockPos(x, y, z);
                    removeBlock(fill);
                    addBlock(fill, BlockType.SANDSTONE);
                }
                for (int y = baseY + 1; y <= baseY + 7; y++) {
                    removeBlock(new BlockPos(x, y, z));
                }
                BlockPos floor = new BlockPos(x, baseY, z);
                removeBlock(floor);
                addBlock(floor, BlockType.SANDSTONE);
            }
        }

        // Low outer walls; the south and north sides have wide entrances.
        for (int y = 1; y <= 3; y++) {
            for (int edge = -7; edge <= 7; edge++) {
                boolean doorway = Math.abs(edge) <= 1;
                if (!doorway) {
                    addBlock(new BlockPos(centerX + edge, baseY + y, centerZ - 7), BlockType.SANDSTONE);
                    addBlock(new BlockPos(centerX + edge, baseY + y, centerZ + 7), BlockType.SANDSTONE);
                }
                addBlock(new BlockPos(centerX - 7, baseY + y, centerZ + edge), BlockType.SANDSTONE);
                addBlock(new BlockPos(centerX + 7, baseY + y, centerZ + edge), BlockType.SANDSTONE);
            }
        }

        // Four tall corner pillars and a stepped roof ring leave the central arena open.
        for (int dx : new int[]{-6, 6}) {
            for (int dz : new int[]{-6, 6}) {
                for (int y = 1; y <= 5; y++) {
                    addBlock(new BlockPos(centerX + dx, baseY + y, centerZ + dz),
                            y == 5 ? BlockType.GLOWING_CRYSTAL : BlockType.SANDSTONE);
                }
            }
        }
        for (int edge = -7; edge <= 7; edge++) {
            if (Math.abs(edge) > 2) {
                addBlock(new BlockPos(centerX + edge, baseY + 4, centerZ - 7), BlockType.SANDSTONE);
                addBlock(new BlockPos(centerX + edge, baseY + 4, centerZ + 7), BlockType.SANDSTONE);
                addBlock(new BlockPos(centerX - 7, baseY + 4, centerZ + edge), BlockType.SANDSTONE);
                addBlock(new BlockPos(centerX + 7, baseY + 4, centerZ + edge), BlockType.SANDSTONE);
            }
        }
        for (int dx : new int[]{-3, 3}) {
            for (int dz : new int[]{-3, 3}) {
                addBlock(new BlockPos(centerX + dx, baseY + 1, centerZ + dz), BlockType.AMETHYST_ORE);
            }
        }
        // A rune pillar and artifact cache sit beside, not inside, the guardian's spawn tile.
        addBlock(new BlockPos(centerX + 3, baseY + 1, centerZ - 2), BlockType.SANDSTONE);
        addBlock(new BlockPos(centerX + 3, baseY + 2, centerZ - 2), BlockType.GLOWING_CRYSTAL);
        BlockPos cache = new BlockPos(centerX - 3, baseY + 1, centerZ + 2);
        removeBlock(cache);
        addBlock(cache, BlockType.ANCIENT_CACHE);

        // Short entrance steps make both temple faces accessible.
        for (int step = 1; step <= 2; step++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos stair = new BlockPos(centerX + dx, baseY, centerZ + 7 + step);
                removeBlock(stair);
                addBlock(stair, BlockType.SANDSTONE);
            }
        }

        addCreature(new WorldCreature(guardianId, CreatureType.TEMPLE_WARDEN,
                centerX + 0.5f, baseY + 1f, centerZ + 0.5f, 0f));
    }

    private static boolean supportsAncientTemples(String versionId) {
        if (versionId == null) return false;
        try {
            String[] parts = versionId.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major > 1 || (major == 1 && (minor > 9 || (minor == 9 && patch >= 8)));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void addVillageMarketStall(int baseX, int baseZ) {
        int startX = baseX + 7;
        int startZ = baseZ + 1;
        int groundY = terrainHeightFor(startX + 2, startZ + 1);
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                for (int dy = 1; dy <= 4; dy++) removeBlock(new BlockPos(startX + dx, groundY + dy, startZ + dz));
            }
        }
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    boolean post = (dx == 0 || dx == 4) && (dz == 0 || dz == 2);
                    boolean roof = dy == 3;
                    if (post || roof) addBlock(new BlockPos(startX + dx, groundY + dy, startZ + dz), BlockType.WOOD);
                }
                if (dz == 0 || dz == 2) addBlock(new BlockPos(startX + dx, groundY + 1, startZ + dz), BlockType.WOOD);
            }
        }
    }

    private void addVillageGarden(int baseX, int baseZ) {
        int startX = baseX + 6;
        int startZ = baseZ;
        for (int x = startX; x < startX + 5; x++) {
            for (int z = startZ; z < startZ + 5; z++) {
                int groundY = terrainHeightFor(x, z);
                for (int y = groundY + 1; y <= groundY + 4; y++) removeBlock(new BlockPos(x, y, z));
                removeBlock(new BlockPos(x, groundY, z));
                addBlock(new BlockPos(x, groundY, z), BlockType.FARMLAND);
                if ((x + z) % 2 == 0) addBlock(new BlockPos(x, groundY + 1, z), BlockType.WHEAT_CROP);
            }
        }
    }

    private void addVillageHouse(int baseX, int baseZ) {
        int groundY = terrainHeightFor(baseX + 2, baseZ + 2);
        // Clear vegetation inside the small 5x5 footprint before placing the house.
        for (int x = baseX; x < baseX + 5; x++) {
            for (int z = baseZ; z < baseZ + 5; z++) {
                for (int y = groundY + 1; y <= groundY + 4; y++) removeBlock(new BlockPos(x, y, z));
                addBlock(new BlockPos(x, groundY + 1, z), BlockType.WOOD);
            }
        }
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                boolean wall = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                if (wall) {
                    for (int dy = 2; dy <= 3; dy++) {
                        boolean doorway = dz == 0 && dx == 2;
                        boolean window = dy == 2 && ((dx == 0 || dx == 4) && dz == 2);
                        if (!doorway && !window) addBlock(new BlockPos(baseX + dx, groundY + dy, baseZ + dz), BlockType.WOOD);
                    }
                }
                addBlock(new BlockPos(baseX + dx, groundY + 4, baseZ + dz), BlockType.WOOD);
            }
        }
        // A short dirt path marks the entrance of each small village home.
        for (int step = 1; step <= 3; step++) {
            BlockPos path = new BlockPos(baseX + 2, groundY, baseZ - step);
            removeBlock(path);
            addBlock(path, BlockType.DIRT);
        }
    }

    private void spawnCreatures(Random random, int radius) {
        long id = 1L;
        // A villager is placed outside each simple wooden village house.
        int firstY = terrainHeightFor(-32, 26);
        int secondY = terrainHeightFor(31, 26);
        // Keep the original generic villager ID represented for old gameplay and saves.
        addCreature(new WorldCreature(id++, CreatureType.VILLAGER, -31.5f, firstY + 1f, 26.5f, 0f));
        addCreature(new WorldCreature(id++, CreatureType.VILLAGER_BLACKSMITH, 31.5f, secondY + 1f, 26.5f, 1.2f));
        int farmerY = highestBlockY(-29, 24);
        if (farmerY >= -12 && !blocks.containsKey(new BlockPos(-29, farmerY + 1, 24))
                && !blocks.containsKey(new BlockPos(-29, farmerY + 2, 24))) {
            addCreature(new WorldCreature(id++, CreatureType.VILLAGER_FARMER, -28.5f, farmerY + 1f, 24.5f, .4f));
        }
        WorldCreature librarian = findCreatureSpawn(random, radius, id++, CreatureType.VILLAGER_LIBRARIAN);
        if (librarian != null) addCreature(librarian);
        WorldCreature guard = findCreatureSpawn(random, radius, id++, CreatureType.VILLAGER_GUARD);
        if (guard != null) addCreature(guard);
        int golemY = highestBlockY(-28, 29);
        if (golemY >= -12 && !blocks.containsKey(new BlockPos(-28, golemY + 1, 29))
                && !blocks.containsKey(new BlockPos(-28, golemY + 2, 29))) {
            addCreature(new WorldCreature(id++, CreatureType.IRON_GOLEM, -27.5f, golemY + 1f, 29.5f, 0f));
        }

        CreatureType[] farmAnimals = {CreatureType.COW, CreatureType.SHEEP, CreatureType.PIG, CreatureType.CHICKEN};
        for (int i = 0; i < 18; i++) {
            CreatureType type = farmAnimals[i % farmAnimals.length];
            WorldCreature creature = findCreatureSpawn(random, radius, id++, type);
            if (creature != null) addCreature(creature);
        }
        for (int i = 0; i < 3; i++) {
            WorldCreature creature = findCreatureSpawn(random, radius, id++, CreatureType.SLIME);
            if (creature != null) addCreature(creature);
        }
        for (int i = 0; i < 4; i++) {
            CreatureType enemy = switch (i % 3) {
                case 0 -> CreatureType.ZOMBIE;
                case 1 -> CreatureType.SKELETON;
                default -> CreatureType.SPIDER;
            };
            WorldCreature creature = findCreatureSpawn(random, radius, id++, enemy);
            if (creature != null) addCreature(creature);
        }
        for (int i = 0; i < 4; i++) {
            WorldCreature creature = findCreatureSpawn(random, radius, id++, CreatureType.FOX);
            if (creature != null) addCreature(creature);
        }
        for (int i = 0; i < 3; i++) {
            WorldCreature creature = findCreatureSpawn(random, radius, id++, CreatureType.TURTLE);
            if (creature != null) addCreature(creature);
        }
        for (int i = 0; i < 5; i++) {
            WorldCreature rabbit = findCreatureSpawn(random, radius, id++, CreatureType.RABBIT);
            if (rabbit != null) addCreature(rabbit);
        }
        int dogX = 4;
        int dogZ = 8;
        int dogY = highestBlockY(dogX, dogZ);
        BlockType dogSurface = blocks.get(new BlockPos(dogX, dogY, dogZ));
        if (dogY >= -12 && (dogSurface == BlockType.GRASS || dogSurface == BlockType.PODZOL)
                && !blocks.containsKey(new BlockPos(dogX, dogY + 1, dogZ))
                && !blocks.containsKey(new BlockPos(dogX, dogY + 2, dogZ))) {
            addCreature(new WorldCreature(id, CreatureType.DOG, dogX + 0.5f, dogY + 1f, dogZ + 0.5f, 0f));
        } else {
            WorldCreature companion = findCreatureSpawn(random, radius, id, CreatureType.DOG);
            if (companion != null) addCreature(companion);
        }
    }

    private WorldCreature findCreatureSpawn(Random random, int radius, long id, CreatureType type) {
        for (int attempt = 0; attempt < 500; attempt++) {
            int x = random.nextInt(radius * 2 - 12) - radius + 6;
            int z = random.nextInt(radius * 2 - 12) - radius + 6;
            if (x * x + z * z < 13 * 13) continue;
            int surfaceY = highestBlockY(x, z);
            if (surfaceY < -12) continue;
            BlockType surface = blocks.get(new BlockPos(x, surfaceY, z));
            BiomeType biome = getBiomeAt(x, z);
            if (type == CreatureType.TURTLE) {
                if (surface != BlockType.SAND) continue;
            } else if (type == CreatureType.FOX) {
                boolean forestFloor = surface == BlockType.GRASS || surface == BlockType.PODZOL;
                if (!forestFloor || (biome != BiomeType.FOREST && biome != BiomeType.TAIGA)) continue;
            } else if (type == CreatureType.RABBIT) {
                boolean greenHabitat = surface == BlockType.GRASS
                        && (biome == BiomeType.PLAINS || biome == BiomeType.FOREST);
                boolean taigaHabitat = surface == BlockType.PODZOL && biome == BiomeType.TAIGA;
                boolean snowyHabitat = surface == BlockType.SNOW && biome == BiomeType.SNOWY_TUNDRA;
                if (!greenHabitat && !taigaHabitat && !snowyHabitat) continue;
            } else if (surface != BlockType.GRASS && surface != BlockType.PODZOL) continue;
            if (blocks.containsKey(new BlockPos(x, surfaceY + 1, z))
                    || blocks.containsKey(new BlockPos(x, surfaceY + 2, z))) continue;
            boolean crowded = creatures.stream().anyMatch(existing -> {
                float dx = existing.x() - (x + 0.5f);
                float dz = existing.z() - (z + 0.5f);
                return dx * dx + dz * dz < 9f;
            });
            if (crowded) continue;
            return new WorldCreature(id, type, x + 0.5f, surfaceY + 1f, z + 0.5f,
                    random.nextFloat() * (float) (Math.PI * 2.0));
        }
        return null;
    }

    private static boolean supportsExtendedBiomes(String versionId) {
        if (versionId == null) return false;
        String[] parts = versionId.split("\\.");
        if (parts.length < 2) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major > 1 || (major == 1 && (minor > 1 || (minor == 1 && patch >= 0)));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean supportsBiomes(String versionId) {
        if (versionId == null) return false;
        String[] parts = versionId.split("\\.");
        if (parts.length < 2) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major > 0 || minor > 9 || (minor == 9 && patch >= 5);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private int terrainHeightFor(int x, int z) {
        return terrainHeightFor(x, z, getBiomeAt(x, z));
    }

    private int terrainHeightFor(int x, int z, BiomeType biome) {
        int base = terrainHeight(x, z);
        return activeBiomes ? base + BiomeGenerator.mountainHeightBonus(seed, x, z, biome) : base;
    }

    private int terrainHeight(int x, int z) {
        double wave = Math.sin(x * 0.27) * 0.85 + Math.cos(z * 0.23) * 0.75
                + Math.sin((x + z) * 0.13) * 0.55;
        return Math.max(0, 2 + (int) Math.round(wave));
    }

    private boolean isCave(int x, int y, int z) {
        double a = Math.sin(x * 0.27 + z * 0.08 + y * 0.11);
        double b = Math.cos(z * 0.24 - x * 0.09 - y * 0.13);
        double c = Math.sin((x + z) * 0.17 - y * 0.08);
        return Math.abs(a + b + c) < 0.20;
    }

    private BlockType oreFor(int x, int y, int z) {
        float roll = coordinateRandom(x, y, z);
        if (y <= -9 && roll < 0.025f) return BlockType.GOLD_ORE;
        if (y <= -4 && roll < 0.075f) return BlockType.IRON_ORE;
        if (roll < 0.15f) return BlockType.COAL_ORE;
        return BlockType.STONE;
    }

    private BlockType oreForRelease100(int x, int y, int z) {
        return oreForRelease100(y, coordinateRandom(x, y, z));
    }

    private BlockType oreForDeepCaves195(int x, int y, int z) {
        float roll = coordinateRandom(Math.floorDiv(x, 2), Math.floorDiv(y, 2), Math.floorDiv(z, 2));
        return OreRules.selectDeepCaves195(y, roll);
    }

    /** Pure ore selection used by the 1.0.0 generator and deterministic tests. */
    public static BlockType oreForRelease100(int y, float roll) {
        return OreRules.selectRelease100(y, roll);
    }

    private static boolean supportsDeepCaves(String id) {
        if (id == null) return false;
        try {
            String[] parts = id.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major > 1 || (major == 1 && (minor > 9 || (minor == 9 && patch >= 5)));
        } catch (RuntimeException ex) { return false; }
    }

    private static boolean supportsCrystalBosses(String versionId) {
        if (versionId == null) return false;
        try {
            String[] parts = versionId.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major > 1 || (major == 1 && (minor > 9 || (minor == 9 && patch >= 7)));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean supportsRelease100(String id) {
        if (id == null) return false;
        try {
            String[] parts = id.split("\\.");
            int major = Integer.parseInt(parts[0]);
            return major >= 1;
        } catch (RuntimeException ex) { return false; }
    }

    private float coordinateRandom(int x, int y, int z) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) z * 0x165667B19E3779F9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value & 0xFFFFFFL) / 16777216f;
    }

    private void addTree(int x, int z) {
        int baseY = terrainHeightFor(x, z) + 1;
        for (int y = baseY; y < baseY + 4; y++) addBlock(new BlockPos(x, y, z), BlockType.WOOD);
        int crownY = baseY + 3;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (!(dx == 0 && dz == 0 && dy == -1)) addBlock(new BlockPos(x + dx, crownY + dy, z + dz), BlockType.LEAVES);
                }
            }
        }
        addBlock(new BlockPos(x, crownY + 2, z), BlockType.LEAVES);
    }

    private void clear() {
        node.detachAllChildren();
        blocks.clear();
        creatures.clear();
        preservedUnknownIds.clear();
        chunkBlocks.clear();
        chunkGeometries.clear();
        triangleHits.clear();
        columnHeights.clear();
        pendingChunks.clear();
        desiredChunks.clear();
        lastStreamCenter = null;
        lastMeshError = "";
        hasBounds = false;
        activeBiomes = false;
        extendedBiomes = false;
        caveBiomesEnabled = false;
        enchantedBiomeEnabled = false;
        worldRadius = 0;
        landmarks = List.of();
        seed = 20261112L;
        minBlockX = maxBlockX = minBlockZ = maxBlockZ = 0;
    }

    public void save(Path file, GameVersion version) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        // Stream directly to disk: sorting and building one giant String doubled peak memory
        // for large worlds and made the first save after generation unnecessarily slow.
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writer.write(FILE_HEADER); writer.newLine();
            writer.write("format=" + VersionManager.WORLD_FORMAT); writer.newLine();
            writer.write("gameVersion=" + version.id()); writer.newLine();
            writer.write("seed=" + seed); writer.newLine();
            writer.write("worldRadius=" + worldRadius); writer.newLine();
            writer.write("biomes=" + activeBiomes); writer.newLine();
            writer.write("extendedBiomes=" + extendedBiomes); writer.newLine();
            writer.write("enchantedBiomes=" + enchantedBiomeEnabled); writer.newLine();
            writer.write("caveBiomes=" + caveBiomesEnabled); writer.newLine();
            writer.write("--blocks--"); writer.newLine();
            for (Map.Entry<BlockPos, BlockType> entry : blocks.entrySet()) {
                BlockPos p = entry.getKey();
                String id = preservedUnknownIds.getOrDefault(p, entry.getValue().id());
                writer.write(Integer.toString(p.x()));
                writer.write(',');
                writer.write(Integer.toString(p.y()));
                writer.write(',');
                writer.write(Integer.toString(p.z()));
                writer.write(',');
                writer.write(id);
                writer.newLine();
            }
            writer.write("--creatures--"); writer.newLine();
            for (WorldCreature creature : creatures) {
                writer.write(Long.toString(creature.id()));
                writer.write(',');
                writer.write(creature.type().id());
                writer.write(',');
                writer.write(Float.toString(creature.x()));
                writer.write(',');
                writer.write(Float.toString(creature.y()));
                writer.write(',');
                writer.write(Float.toString(creature.z()));
                writer.write(',');
                writer.write(Float.toString(creature.yaw()));
                writer.newLine();
            }
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Loads a legacy-compatible block save without keeping the whole file in RAM. */
    public String load(Path file) throws IOException {
        boolean dataStarted = false;
        String savedVersion;
        long loadedSeed;
        boolean loadedActiveBiomes;
        boolean loadedExtendedBiomes;
        boolean loadedEnchantedBiomes;
        boolean loadedCaveBiomes;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !FILE_HEADER.equals(header.trim())) {
                throw new IOException("Это не файл сохранения BlockWorld.");
            }

            int lineNumber = 1;
            int format = -1;
            String versionProperty = "неизвестно";
            String biomeProperty = "";
            String extendedBiomeProperty = "";
            String enchantedBiomeProperty = "";
            String caveBiomeProperty = "";
            String seedProperty = "20261112";
            String worldRadiusProperty = "";
            boolean foundBlocks = false;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String value = line.trim();
                if ("--blocks--".equals(value)) {
                    foundBlocks = true;
                    break;
                }
                if (value.startsWith("format=")) format = parseIntOr(value.substring(7), -1);
                else if (value.startsWith("gameVersion=")) versionProperty = value.substring(12).trim();
                else if (value.startsWith("biomes=")) biomeProperty = value.substring(7).trim();
                else if (value.startsWith("extendedBiomes=")) extendedBiomeProperty = value.substring(15).trim();
                else if (value.startsWith("enchantedBiomes=")) enchantedBiomeProperty = value.substring(16).trim();
                else if (value.startsWith("caveBiomes=")) caveBiomeProperty = value.substring(11).trim();
                else if (value.startsWith("seed=")) seedProperty = value.substring(5).trim();
                else if (value.startsWith("worldRadius=")) worldRadiusProperty = value.substring(12).trim();
            }
            if (!foundBlocks) throw new IOException("В файле отсутствует список блоков.");
            if (format > VersionManager.WORLD_FORMAT) {
                throw new IOException("Мир использует более новый формат сохранения (" + format + "). Обнови BlockWorld.");
            }
            if (format < 1) throw new IOException("Формат мира повреждён или не поддерживается.");

            savedVersion = versionProperty.isBlank() ? "неизвестно" : versionProperty;
            loadedSeed = parseLongOr(seedProperty, 20261112L);
            loadedActiveBiomes = "true".equalsIgnoreCase(biomeProperty) ? true
                    : "false".equalsIgnoreCase(biomeProperty) ? false : supportsBiomes(savedVersion);
            loadedExtendedBiomes = "true".equalsIgnoreCase(extendedBiomeProperty) ? true
                    : "false".equalsIgnoreCase(extendedBiomeProperty) ? false : supportsExtendedBiomes(savedVersion);
            loadedEnchantedBiomes = "true".equalsIgnoreCase(enchantedBiomeProperty) ? true
                    : "false".equalsIgnoreCase(enchantedBiomeProperty) ? false
                    : BiomeGenerator.supportsEnchantedGrove(savedVersion);
            loadedCaveBiomes = "true".equalsIgnoreCase(caveBiomeProperty) ? true
                    : "false".equalsIgnoreCase(caveBiomeProperty) ? false : CaveBiomeGenerator.supports(savedVersion);

            clear();
            dataStarted = true;
            activeBiomes = loadedActiveBiomes;
            extendedBiomes = loadedExtendedBiomes;
            enchantedBiomeEnabled = loadedEnchantedBiomes;
            caveBiomesEnabled = loadedCaveBiomes;
            seed = loadedSeed;
            int defaultRadius = VersionManager.find(savedVersion).worldRadius();
            int savedRadius = parseIntOr(worldRadiusProperty, defaultRadius);
            worldRadius = savedRadius >= 1 && savedRadius <= 512 ? savedRadius : defaultRadius;
            bulkUpdate = true;

            int loadedBlocks = 0;
            int loadedCreatures = 0;
            boolean readingCreatures = false;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String value = line.trim();
                if (value.isEmpty()) continue;
                if ("--creatures--".equals(value)) {
                    readingCreatures = true;
                    continue;
                }
                if (!readingCreatures) {
                    if (++loadedBlocks > MAX_SAVED_BLOCKS) {
                        throw new IOException("В мире слишком много блоков. Загрузка отменена для безопасности.");
                    }
                    try {
                        int first = value.indexOf(',');
                        int second = first < 0 ? -1 : value.indexOf(',', first + 1);
                        int third = second < 0 ? -1 : value.indexOf(',', second + 1);
                        if (first <= 0 || second <= first + 1 || third <= second + 1
                                || value.indexOf(',', third + 1) >= 0) {
                            throw new IllegalArgumentException("Ожидались координаты и ID блока");
                        }
                        int x = Integer.parseInt(value.substring(0, first).trim());
                        int y = Integer.parseInt(value.substring(first + 1, second).trim());
                        int z = Integer.parseInt(value.substring(second + 1, third).trim());
                        String rawId = value.substring(third + 1).trim();
                        if (!isValidBlockId(rawId)) throw new IllegalArgumentException("Недопустимый ID блока");
                        BlockType type;
                        boolean unknown = false;
                        try {
                            type = BlockType.fromId(rawId);
                        } catch (IllegalArgumentException ex) {
                            type = BlockType.STONE;
                            unknown = true;
                        }
                        BlockPos pos = new BlockPos(x, y, z);
                        if (addBlock(pos, type) && unknown) preservedUnknownIds.put(pos, rawId);
                    } catch (RuntimeException ex) {
                        throw new IOException("Некорректный блок в строке " + lineNumber + ".", ex);
                    }
                } else {
                    if (++loadedCreatures > 256) {
                        throw new IOException("В мире слишком много существ. Загрузка отменена для безопасности.");
                    }
                    String[] parts = value.split(",", -1);
                    if (parts.length != 6) {
                        throw new IOException("Повреждена запись существа в строке " + lineNumber + ".");
                    }
                    try {
                        WorldCreature creature = new WorldCreature(
                                Long.parseLong(parts[0].trim()), CreatureType.fromId(parts[1].trim()),
                                Float.parseFloat(parts[2].trim()), Float.parseFloat(parts[3].trim()),
                                Float.parseFloat(parts[4].trim()), Float.parseFloat(parts[5].trim()));
                        if (!addCreature(creature)) {
                            throw new IllegalArgumentException("Повторный ID существа или превышен лимит");
                        }
                    } catch (RuntimeException ex) {
                        throw new IOException("Некорректное существо в строке " + lineNumber + ".", ex);
                    }
                }
            }
        } catch (IOException ex) {
            if (dataStarted) clear();
            throw ex;
        } finally {
            bulkUpdate = false;
        }

        if (WorldNavigation.supportsExploration(savedVersion)) {
            landmarks = WorldNavigation.allMarkers(loadedSeed, worldRadius);
        }
        return savedVersion;
    }

    private int parseIntOr(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private long parseLongOr(String value, long fallback) {
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private boolean isValidBlockId(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!(ch >= 'A' && ch <= 'Z') && !(ch >= 'a' && ch <= 'z')
                    && !(ch >= '0' && ch <= '9') && ch != '_' && ch != '-') return false;
        }
        return true;
    }

    private record Face(int nx, int ny, int nz, float shade, float[] vertices) { }

}
