package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.FloraCatalog;
import com.blockworld.core.VoxelWorld;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

/**
 * Seeded pixel-like meadow decorations. All visual plants are batched into one
 * vertex-colored mesh to avoid thousands of Geometry and Material allocations.
 */
public final class NatureDecorationManager {
    private static final int MAX_DECORATIONS = 1200;
    private final Node node = new Node("MeadowDecorations");
    private final Material material;
    private int density = 2;
    private int decorations;

    public NatureDecorationManager(AssetManager assets) {
        material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setBoolean("VertexColor", true);
        material.setColor("Color", ColorRGBA.White);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
    }

    public Node getNode() { return node; }
    public int size() { return decorations; }

    /** 0=off, 1=low, 2=normal, 3=high. Takes effect on the next rebuild. */
    public void setDensity(int density) {
        this.density = Math.max(0, Math.min(3, density));
    }

    public void rebuild(VoxelWorld world) {
        node.detachAllChildren();
        decorations = 0;
        if (world == null || density == 0) return;

        int flowerThreshold = switch (density) {
            case 1 -> 3;
            case 3 -> 14;
            default -> 9;
        };
        int grassThreshold = switch (density) {
            case 1 -> 10;
            case 3 -> 45;
            default -> 29;
        };
        int decorationLimit = density == 3 ? MAX_DECORATIONS + 600 : MAX_DECORATIONS;
        MeshBuilder mesh = new MeshBuilder();
        world.forEachBlock((ground, type) -> {
            if (decorations >= decorationLimit || type != BlockType.GRASS) return;
            if (world.contains(ground.add(0, 1, 0))) return;

            long hash = mix(world.getSeed(), ground.x(), ground.z());
            int sample = (int) Math.floorMod(hash, 100L);
            float x = ground.x() + .5f;
            float y = ground.y() + 1.01f;
            float z = ground.z() + .5f;
            if (sample < flowerThreshold) {
                addFlower(mesh, x, y, z,
                        FloraCatalog.flowerAt(world.getSeed(), ground.x(), ground.z(),
                                world.getBiomeAt(ground.x(), ground.z())));
                decorations++;
            } else if (sample < grassThreshold) {
                FloraCatalog.Grass grass = FloraCatalog.grassAt(world.getSeed(), ground.x(), ground.z());
                if (grass == FloraCatalog.Grass.FERN) addFern(mesh, x, y, z);
                else addGrass(mesh, x, y, z, grass == FloraCatalog.Grass.TALL_GRASS);
                decorations++;
            }
        });

        if (!mesh.hasTriangles()) return;
        Mesh batchedMesh = new Mesh();
        batchedMesh.setMode(Mesh.Mode.Triangles);
        batchedMesh.setBuffer(VertexBuffer.Type.Position, 3,
                BufferUtils.createFloatBuffer(mesh.positions.toArray()));
        batchedMesh.setBuffer(VertexBuffer.Type.Color, 4,
                BufferUtils.createFloatBuffer(mesh.colors.toArray()));
        batchedMesh.setBuffer(VertexBuffer.Type.Index, 3,
                BufferUtils.createIntBuffer(mesh.indices.toArray()));
        batchedMesh.updateBound();
        batchedMesh.setStatic();

        Geometry geometry = new Geometry("BatchedMeadowDecorations", batchedMesh);
        geometry.setMaterial(material);
        node.attachChild(geometry);
    }

    private static void addFlower(MeshBuilder mesh, float x, float y, float z,
                                  FloraCatalog.Flower flower) {
        ColorRGBA stem = color(.20f, .47f, .16f);
        mesh.box(x, y + .17f, z, .025f, .17f, .025f, stem);
        ColorRGBA petals = color(flower.red(), flower.green(), flower.blue());
        float petalY = flower == FloraCatalog.Flower.SUNFLOWER ? y + .63f : y + .36f;
        float petalScale = flower == FloraCatalog.Flower.SUNFLOWER ? .12f : .075f;
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2.0 / 5.0;
            mesh.box(x + (float) Math.cos(angle) * .09f, petalY,
                    z + (float) Math.sin(angle) * .09f,
                    petalScale, .055f, .055f, petals);
        }
        mesh.box(x, petalY + .03f, z, .045f, .045f, .045f, color(.98f, .78f, .18f));
        if (flower == FloraCatalog.Flower.SUNFLOWER) {
            mesh.box(x, y + .34f, z, .03f, .26f, .03f, stem);
        }
    }

    private static void addGrass(MeshBuilder mesh, float x, float y, float z, boolean tall) {
        float height = tall ? .62f : .36f;
        ColorRGBA green = tall ? color(.18f, .48f, .12f) : color(.29f, .61f, .18f);
        mesh.verticalQuadX(x - .125f, y, z, .25f, height, green);
        mesh.verticalQuadZ(x, y, z - .125f, .25f, height, green);
    }

    private static void addFern(MeshBuilder mesh, float x, float y, float z) {
        mesh.box(x, y + .20f, z, .025f, .20f, .025f, color(.15f, .43f, .18f));
        for (int i = 0; i < 4; i++) {
            float side = i % 2 == 0 ? -1f : 1f;
            mesh.box(x + side * .09f, y + .12f + i * .085f, z,
                    .11f, .035f, .05f, color(.20f, .55f, .21f));
        }
    }

    private static ColorRGBA color(float r, float g, float b) {
        return new ColorRGBA(r, g, b, 1f);
    }

    private static long mix(long seed, int x, int z) {
        long value = seed ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static final class MeshBuilder {
        private final FloatAccumulator positions = new FloatAccumulator(8192);
        private final FloatAccumulator colors = new FloatAccumulator(8192);
        private final IntAccumulator indices = new IntAccumulator(4096);

        boolean hasTriangles() { return indices.size() > 0; }

        void verticalQuadX(float x, float y, float z, float width, float height, ColorRGBA tint) {
            quad(x, y, z, x + width, y, z, x + width, y + height, z, x, y + height, z, tint);
        }

        void verticalQuadZ(float x, float y, float z, float width, float height, ColorRGBA tint) {
            quad(x, y, z, x, y, z + width, x, y + height, z + width, x, y + height, z, tint);
        }

        void box(float x, float y, float z, float hx, float hy, float hz, ColorRGBA tint) {
            // Six faces, each emitted as four independent vertices for flat color.
            quad(x-hx,y-hy,z-hz, x-hx,y+hy,z-hz, x-hx,y+hy,z+hz, x-hx,y-hy,z+hz, tint);
            quad(x+hx,y-hy,z+hz, x+hx,y+hy,z+hz, x+hx,y+hy,z-hz, x+hx,y-hy,z-hz, tint);
            quad(x-hx,y+hy,z-hz, x+hx,y+hy,z-hz, x+hx,y+hy,z+hz, x-hx,y+hy,z+hz, tint);
            quad(x-hx,y-hy,z+hz, x+hx,y-hy,z+hz, x+hx,y-hy,z-hz, x-hx,y-hy,z-hz, tint);
            quad(x+hx,y-hy,z+hz, x+hx,y+hy,z+hz, x-hx,y+hy,z+hz, x-hx,y-hy,z+hz, tint);
            quad(x-hx,y-hy,z-hz, x-hx,y+hy,z-hz, x+hx,y+hy,z-hz, x+hx,y-hy,z-hz, tint);
        }

        private void quad(float x0,float y0,float z0, float x1,float y1,float z1,
                          float x2,float y2,float z2, float x3,float y3,float z3, ColorRGBA tint) {
            int first = positions.size() / 3;
            addVertex(x0,y0,z0,tint);
            addVertex(x1,y1,z1,tint);
            addVertex(x2,y2,z2,tint);
            addVertex(x3,y3,z3,tint);
            indices.add(first);
            indices.add(first + 1);
            indices.add(first + 2);
            indices.add(first);
            indices.add(first + 2);
            indices.add(first + 3);
        }

        private void addVertex(float x, float y, float z, ColorRGBA tint) {
            positions.add(x); positions.add(y); positions.add(z);
            colors.add(tint.r); colors.add(tint.g); colors.add(tint.b); colors.add(tint.a);
        }
    }

    private static final class FloatAccumulator {
        private float[] values;
        private int size;

        FloatAccumulator(int initialCapacity) { values = new float[Math.max(16, initialCapacity)]; }
        int size() { return size; }
        void add(float value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }
        float[] toArray() { return java.util.Arrays.copyOf(values, size); }
    }

    private static final class IntAccumulator {
        private int[] values;
        private int size;

        IntAccumulator(int initialCapacity) { values = new int[Math.max(16, initialCapacity)]; }
        int size() { return size; }
        void add(int value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }
        int[] toArray() { return java.util.Arrays.copyOf(values, size); }
    }
}
