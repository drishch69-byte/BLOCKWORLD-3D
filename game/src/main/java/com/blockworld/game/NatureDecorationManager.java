package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.FloraCatalog;
import com.blockworld.core.VoxelWorld;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;

import java.util.Map;

/** Seeded pixel-like meadow decorations. Plants are visual-only, not saved voxel blocks. */
public final class NatureDecorationManager {
    private static final int MAX_DECORATIONS = 1200;
    private final AssetManager assets;
    private final Node node = new Node("MeadowDecorations");
    private int decorations;

    public NatureDecorationManager(AssetManager assets) { this.assets = assets; }
    public Node getNode() { return node; }
    public int size() { return decorations; }

    public void rebuild(VoxelWorld world) {
        node.detachAllChildren();
        decorations = 0;
        if (world == null) return;
        for (Map.Entry<BlockPos, BlockType> entry : world.snapshotBlocks().entrySet()) {
            if (decorations >= MAX_DECORATIONS) break;
            if (entry.getValue() != BlockType.GRASS) continue;
            BlockPos ground = entry.getKey();
            if (world.contains(ground.add(0, 1, 0))) continue;
            long hash = mix(world.getSeed(), ground.x(), ground.z());
            int sample = (int) Math.floorMod(hash, 100L);
            float x = ground.x() + .5f;
            float y = ground.y() + 1.01f;
            float z = ground.z() + .5f;
            if (sample < 9) {
                addFlower(x, y, z, FloraCatalog.flowerAt(world.getSeed(), ground.x(), ground.z(),
                        world.getBiomeAt(ground.x(), ground.z())));
                decorations++;
            } else if (sample < 29) {
                FloraCatalog.Grass type = FloraCatalog.grassAt(world.getSeed(), ground.x(), ground.z());
                if (type == FloraCatalog.Grass.FERN) addFern(x, y, z);
                else addGrass(x, y, z, type == FloraCatalog.Grass.TALL_GRASS);
                decorations++;
            }
        }
    }

    private void addFlower(float x, float y, float z, FloraCatalog.Flower flower) {
        addBox("stem-" + flower.name(), .025f, .17f, .025f,
                x, y + .17f, z, color(.20f, .47f, .16f));
        ColorRGBA petals = color(flower.red(), flower.green(), flower.blue());
        float petalY = flower == FloraCatalog.Flower.SUNFLOWER ? y + .63f : y + .36f;
        float petalScale = flower == FloraCatalog.Flower.SUNFLOWER ? .12f : .075f;
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2.0 / 5.0;
            addBox("petal-" + flower.name(), petalScale, .055f, .055f,
                    x + (float) Math.cos(angle) * .09f, petalY,
                    z + (float) Math.sin(angle) * .09f, petals);
        }
        addBox("center-" + flower.name(), .045f, .045f, .045f,
                x, petalY + .03f, z, color(.98f, .78f, .18f));
        if (flower == FloraCatalog.Flower.SUNFLOWER) {
            addBox("sunflower-stem", .03f, .26f, .03f, x, y + .34f, z, color(.19f, .45f, .15f));
        }
    }

    private void addGrass(float x, float y, float z, boolean tall) {
        float height = tall ? .62f : .36f;
        ColorRGBA green = tall ? color(.18f, .48f, .12f) : color(.29f, .61f, .18f);
        Geometry first = quad("grass-blade-a", .25f, height, green);
        first.setLocalTranslation(x - .125f, y, z);
        Geometry second = quad("grass-blade-b", .25f, height, green);
        second.setLocalTranslation(x - .125f, y, z);
        second.setLocalRotation(new Quaternion().fromAngleAxis(1.57f, Vector3f.UNIT_Y));
        node.attachChild(first);
        node.attachChild(second);
    }

    private void addFern(float x, float y, float z) {
        addBox("fern-stem", .025f, .20f, .025f, x, y + .20f, z, color(.15f, .43f, .18f));
        for (int i = 0; i < 4; i++) {
            float side = i % 2 == 0 ? -1f : 1f;
            addBox("fern-leaf", .11f, .035f, .05f, x + side * .09f,
                    y + .12f + i * .085f, z, color(.20f, .55f, .21f));
        }
    }

    private Geometry quad(String name, float width, float height, ColorRGBA tint) {
        Geometry geometry = new Geometry(name, new Quad(width, height));
        Material material = material(tint);
        geometry.setMaterial(material);
        return geometry;
    }

    private void addBox(String name, float hx, float hy, float hz,
                        float x, float y, float z, ColorRGBA tint) {
        Geometry geometry = new Geometry(name, new Box(hx, hy, hz));
        geometry.setMaterial(material(tint));
        geometry.setLocalTranslation(x, y, z);
        node.attachChild(geometry);
    }

    private Material material(ColorRGBA tint) {
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", tint);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return material;
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
}
