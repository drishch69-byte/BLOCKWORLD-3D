package com.blockworld.game;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.VoxelWorld;
import com.blockworld.core.WorldCreature;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Texture;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Low-cost blocky creature visuals and simple deterministic wandering. */
public final class CreatureManager {
    private final AssetManager assets;
    private final Map<String, Texture> creatureTextures = new HashMap<>();
    private final Node node = new Node("WorldCreatures");
    private final Map<Long, Visual> visuals = new HashMap<>();

    public CreatureManager(AssetManager assets) {
        this.assets = assets;
        loadCreatureTextures();
    }

    /** Loads nearest-filtered PNG skins; color-only materials remain the safe fallback. */
    private void loadCreatureTextures() {
        for (CreatureType type : CreatureType.values()) {
            try {
                Texture texture = assets.loadTexture("Textures/Creatures/" + type.id() + ".png");
                texture.setMagFilter(Texture.MagFilter.Nearest);
                texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
                texture.setWrap(Texture.WrapMode.Repeat);
                creatureTextures.put(type.id(), texture);
            } catch (RuntimeException ignored) {
                // Keep the existing color-only appearance when an optional texture is unavailable.
            }
        }
    }

    public Node getNode() { return node; }
    public int size() { return visuals.size(); }

    public WorldCreature nearest(Vector3f position, float maxDistance) {
        WorldCreature closest = null;
        float bestDistanceSquared = maxDistance * maxDistance;
        for (Visual visual : visuals.values()) {
            WorldCreature state = visual.state;
            float dx = state.x() - position.x;
            float dy = state.y() - position.y;
            float dz = state.z() - position.z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                closest = state;
            }
        }
        return closest;
    }

    public void sync(VoxelWorld world) {
        node.detachAllChildren();
        visuals.clear();
        for (WorldCreature creature : world.getCreatures()) {
            Visual visual = createVisual(creature);
            visuals.put(creature.id(), visual);
            node.attachChild(visual.root);
        }
    }

    public void update(float tpf, Vector3f playerPosition, VoxelWorld world) {
        for (Visual visual : visuals.values()) {
            WorldCreature state = visual.state;
            visual.turnTimer -= tpf;
            if (visual.turnTimer <= 0f) {
                visual.turnTimer = 2.5f + visual.random.nextFloat() * 5.5f;
                visual.yaw = visual.random.nextFloat() * (float) (Math.PI * 2.0);
                visual.pauseTimer = 0.5f + visual.random.nextFloat() * 2.4f;
            }

            float nextX = state.x();
            float nextZ = state.z();
            if (visual.pauseTimer > 0f) {
                visual.pauseTimer -= tpf;
            } else {
                float step = state.type().moveSpeed() * Math.min(tpf, 0.05f);
                nextX += (float) Math.sin(visual.yaw) * step;
                nextZ += (float) Math.cos(visual.yaw) * step;
            }

            if (playerPosition != null) {
                float playerDx = nextX - playerPosition.x;
                float playerDz = nextZ - playerPosition.z;
                if (playerDx * playerDx + playerDz * playerDz < 1.8f) visual.pauseTimer = Math.max(visual.pauseTimer, 0.8f);
            }
            int blockX = (int) Math.floor(nextX);
            int blockZ = (int) Math.floor(nextZ);
            int surfaceY = world.highestBlockY(blockX, blockZ);
            if (surfaceY >= -12 && Math.abs((surfaceY + 1f) - state.y()) <= 1.25f) {
                BlockType surface = world.getBlock(new BlockPos(blockX, surfaceY, blockZ));
                boolean safeSurface = surface == BlockType.GRASS || surface == BlockType.DIRT || surface == BlockType.SAND;
                boolean clearAbove = !world.contains(new BlockPos(blockX, surfaceY + 1, blockZ))
                        && !world.contains(new BlockPos(blockX, surfaceY + 2, blockZ));
                if (safeSurface && clearAbove) {
                    state = new WorldCreature(state.id(), state.type(), nextX, surfaceY + 1f, nextZ, visual.yaw);
                    visual.state = state;
                    world.updateCreature(state);
                } else {
                    visual.pauseTimer = 1.0f;
                }
            } else {
                visual.pauseTimer = 1.0f;
            }
            visual.root.setLocalTranslation(state.x(), state.y(), state.z());
            visual.root.setLocalRotation(new Quaternion().fromAngleAxis(state.yaw(), Vector3f.UNIT_Y));
        }
    }

    private Visual createVisual(WorldCreature creature) {
        Node root = new Node("Creature-" + creature.type().id() + "-" + creature.id());
        switch (creature.type()) {
            case COW -> buildCow(root);
            case SHEEP -> buildSheep(root);
            case PIG -> buildPig(root);
            case CHICKEN -> buildChicken(root);
            case VILLAGER -> buildVillager(root);
            case SLIME -> buildSlime(root);
        }
        root.setLocalTranslation(creature.x(), creature.y(), creature.z());
        root.setLocalRotation(new Quaternion().fromAngleAxis(creature.yaw(), Vector3f.UNIT_Y));
        return new Visual(creature, root);
    }

    private void buildCow(Node root) {
        addBox(root, "cow-body", .49f, .34f, .76f, 0f, .65f, 0f, color(.88f, .86f, .78f));
        addBox(root, "cow-spot-a", .18f, .12f, .20f, -.29f, .77f, -.15f, color(.32f, .20f, .12f));
        addBox(root, "cow-spot-b", .16f, .10f, .19f, .29f, .55f, .26f, color(.32f, .20f, .12f));
        addBox(root, "cow-head", .25f, .26f, .28f, 0f, .85f, -.79f, color(.48f, .30f, .19f));
        addBox(root, "cow-snout", .17f, .11f, .12f, 0f, .68f, -1.10f, color(.88f, .59f, .53f));
        legs(root, color(.28f, .23f, .19f), .12f, .27f, .13f);
        addBox(root, "cow-ear-left", .08f, .07f, .16f, -.29f, .88f, -.77f, color(.60f, .39f, .30f));
        addBox(root, "cow-ear-right", .08f, .07f, .16f, .29f, .88f, -.77f, color(.60f, .39f, .30f));
    }

    private void buildSheep(Node root) {
        ColorRGBA wool = color(.91f, .91f, .86f);
        addBox(root, "sheep-wool", .50f, .40f, .70f, 0f, .67f, 0f, wool);
        addBox(root, "sheep-wool-top", .31f, .13f, .34f, 0f, 1.04f, -.08f, wool);
        addBox(root, "sheep-head", .22f, .24f, .25f, 0f, .82f, -.79f, color(.58f, .48f, .39f));
        addBox(root, "sheep-muzzle", .13f, .09f, .09f, 0f, .67f, -1.07f, color(.76f, .60f, .55f));
        legs(root, color(.37f, .32f, .28f), .10f, .27f, .11f);
    }

    private void buildPig(Node root) {
        ColorRGBA pink = color(.91f, .55f, .57f);
        addBox(root, "pig-body", .48f, .32f, .69f, 0f, .63f, 0f, pink);
        addBox(root, "pig-head", .25f, .24f, .26f, 0f, .83f, -.76f, pink);
        addBox(root, "pig-snout", .17f, .12f, .08f, 0f, .73f, -1.06f, color(.77f, .36f, .40f));
        legs(root, color(.73f, .39f, .43f), .11f, .25f, .12f);
        addBox(root, "pig-ear-left", .09f, .12f, .07f, -.20f, 1.10f, -.80f, pink);
        addBox(root, "pig-ear-right", .09f, .12f, .07f, .20f, 1.10f, -.80f, pink);
    }

    private void buildChicken(Node root) {
        addBox(root, "chicken-body", .29f, .34f, .36f, 0f, .57f, 0f, color(.91f, .89f, .82f));
        addBox(root, "chicken-head", .18f, .20f, .18f, 0f, .91f, -.31f, color(.95f, .94f, .88f));
        addBox(root, "chicken-beak", .08f, .06f, .12f, 0f, .85f, -.58f, color(.96f, .55f, .12f));
        addBox(root, "chicken-comb", .07f, .10f, .06f, 0f, 1.18f, -.30f, color(.78f, .12f, .12f));
        addBox(root, "chicken-leg-left", .045f, .18f, .045f, -.12f, .18f, 0f, color(.90f, .60f, .12f));
        addBox(root, "chicken-leg-right", .045f, .18f, .045f, .12f, .18f, 0f, color(.90f, .60f, .12f));
    }

    private void buildVillager(Node root) {
        addBox(root, "villager-robe", .34f, .52f, .27f, 0f, .69f, 0f, color(.44f, .32f, .20f));
        addBox(root, "villager-head", .23f, .24f, .23f, 0f, 1.43f, -.02f, color(.75f, .57f, .43f));
        addBox(root, "villager-nose", .06f, .08f, .13f, 0f, 1.36f, -.31f, color(.66f, .45f, .35f));
        addBox(root, "villager-hat", .25f, .07f, .25f, 0f, 1.70f, -.02f, color(.35f, .25f, .15f));
        addBox(root, "villager-arm-left", .10f, .28f, .11f, -.40f, .68f, 0f, color(.55f, .40f, .27f));
        addBox(root, "villager-arm-right", .10f, .28f, .11f, .40f, .68f, 0f, color(.55f, .40f, .27f));
        addBox(root, "villager-leg-left", .12f, .18f, .13f, -.16f, .18f, .02f, color(.30f, .25f, .20f));
        addBox(root, "villager-leg-right", .12f, .18f, .13f, .16f, .18f, .02f, color(.30f, .25f, .20f));
    }

    private void buildSlime(Node root) {
        addBox(root, "slime-body", .40f, .38f, .40f, 0f, .48f, 0f, color(.22f, .78f, .37f));
        addBox(root, "slime-eye-left", .055f, .07f, .035f, -.16f, .58f, -.41f, color(0f, 0f, 0f));
        addBox(root, "slime-eye-right", .055f, .07f, .035f, .16f, .58f, -.41f, color(0f, 0f, 0f));
        addBox(root, "slime-mouth", .09f, .025f, .025f, 0f, .35f, -.42f, color(.06f, .22f, .10f));
    }

    private void legs(Node root, ColorRGBA color, float halfX, float halfY, float halfZ) {
        addBox(root, "leg-front-left", halfX, halfY, halfZ, -.28f, halfY, -.49f, color);
        addBox(root, "leg-front-right", halfX, halfY, halfZ, .28f, halfY, -.49f, color);
        addBox(root, "leg-back-left", halfX, halfY, halfZ, -.28f, halfY, .49f, color);
        addBox(root, "leg-back-right", halfX, halfY, halfZ, .28f, halfY, .49f, color);
    }

    private void addBox(Node parent, String name, float x, float y, float z,
                        float px, float py, float pz, ColorRGBA color) {
        Geometry geometry = new Geometry(name, new Box(x, y, z));
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        String rootName = parent.getName();
        String textureId = rootName != null && rootName.startsWith("Creature-")
                ? rootName.substring("Creature-".length()).split("-", 2)[0] : "";
        Texture skin = creatureTextures.get(textureId);
        if (skin != null) material.setTexture("ColorMap", skin);
        material.setColor("Color", color);
        geometry.setMaterial(material);
        geometry.setLocalTranslation(px, py, pz);
        parent.attachChild(geometry);
    }

    private ColorRGBA color(float r, float g, float b) { return new ColorRGBA(r, g, b, 1f); }

    private static final class Visual {
        private WorldCreature state;
        private final Node root;
        private final Random random;
        private float turnTimer;
        private float pauseTimer;
        private float yaw;

        private Visual(WorldCreature state, Node root) {
            this.state = state;
            this.root = root;
            this.random = new Random(state.id() * 0x9E3779B97F4A7C15L + state.type().ordinal());
            this.yaw = state.yaw();
            this.turnTimer = 1f + random.nextFloat() * 3f;
        }
    }
}
