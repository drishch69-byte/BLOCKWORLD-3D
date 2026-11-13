package com.blockworld.game;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

/** Simple animated block-style explorer shown by the third-person camera. */
final class PlayerAvatar {
    private final Node root = new Node("BlockWorldPlayerAvatar");
    private final Geometry shirt;
    private final Geometry head;
    private final Geometry hair;
    private final Geometry leftArm;
    private final Geometry rightArm;
    private final Geometry leftLeg;
    private final Geometry rightLeg;
    private final Geometry leftShoe;
    private final Geometry rightShoe;
    private BitmapText displayName;
    private int styleIndex;
    private float clock;

    PlayerAvatar(AssetManager assets) {
        ColorRGBA skin = new ColorRGBA(0.87f, 0.65f, 0.48f, 1f);
        ColorRGBA shirt = new ColorRGBA(0.17f, 0.47f, 0.72f, 1f);
        ColorRGBA trousers = new ColorRGBA(0.20f, 0.25f, 0.37f, 1f);
        ColorRGBA shoes = new ColorRGBA(0.22f, 0.16f, 0.12f, 1f);
        ColorRGBA hair = new ColorRGBA(0.24f, 0.15f, 0.09f, 1f);
        this.shirt = box(assets, "shirt", .28f, .37f, .16f, 0f, 1.02f, 0f, shirt);
        this.head = box(assets, "head", .22f, .23f, .22f, 0f, 1.64f, 0f, skin);
        this.hair = box(assets, "hair", .23f, .08f, .23f, 0f, 1.92f, .015f, hair);
        box(assets, "eye-left", .035f, .035f, .025f, -.09f, 1.68f, -.225f, ColorRGBA.White);
        box(assets, "eye-right", .035f, .035f, .025f, .09f, 1.68f, -.225f, ColorRGBA.White);
        box(assets, "pupil-left", .015f, .024f, .012f, -.09f, 1.68f, -.25f, ColorRGBA.Black);
        box(assets, "pupil-right", .015f, .024f, .012f, .09f, 1.68f, -.25f, ColorRGBA.Black);
        leftArm = box(assets, "arm-left", .10f, .34f, .11f, -.39f, 1.03f, 0f, skin);
        rightArm = box(assets, "arm-right", .10f, .34f, .11f, .39f, 1.03f, 0f, skin);
        leftLeg = box(assets, "leg-left", .13f, .34f, .14f, -.15f, .36f, 0f, trousers);
        rightLeg = box(assets, "leg-right", .13f, .34f, .14f, .15f, .36f, 0f, trousers);
        leftShoe = box(assets, "shoe-left", .13f, .09f, .18f, -.15f, .09f, -.035f, shoes);
        rightShoe = box(assets, "shoe-right", .13f, .09f, .18f, .15f, .09f, -.035f, shoes);
        applyStyle();
    }

    private static final String[] STYLE_NAMES = {"Исследователь", "Лесной", "Песчаный", "Кристальный"};

    int cycleStyle() {
        return setStyle(styleIndex + 1);
    }

    int setStyle(int index) {
        styleIndex = Math.max(0, Math.min(STYLE_NAMES.length - 1, index));
        applyStyle();
        return styleIndex;
    }

    int styleIndex() { return styleIndex; }

    String styleName() { return STYLE_NAMES[styleIndex]; }

    void setDisplayName(AssetManager assets, String name) {
        if (displayName == null) {
            displayName = new BitmapText(assets.loadFont("Interface/Fonts/Default.fnt"));
            displayName.setSize(0.55f);
            displayName.setColor(ColorRGBA.White);
            displayName.setLocalTranslation(-0.9f, 2.35f, 0.02f);
            root.attachChild(displayName);
        }
        displayName.setText(name == null ? "Player" : name);
    }

    private void applyStyle() {
        ColorRGBA skinColor = new ColorRGBA(0.87f, 0.65f, 0.48f, 1f);
        ColorRGBA shirtColor;
        ColorRGBA trouserColor;
        ColorRGBA shoeColor;
        ColorRGBA hairColor;
        switch (styleIndex) {
            case 1 -> {
                shirtColor = new ColorRGBA(0.20f, 0.49f, 0.28f, 1f);
                trouserColor = new ColorRGBA(0.24f, 0.31f, 0.21f, 1f);
                shoeColor = new ColorRGBA(0.27f, 0.20f, 0.13f, 1f);
                hairColor = new ColorRGBA(0.20f, 0.13f, 0.08f, 1f);
            }
            case 2 -> {
                shirtColor = new ColorRGBA(0.76f, 0.56f, 0.24f, 1f);
                trouserColor = new ColorRGBA(0.43f, 0.31f, 0.19f, 1f);
                shoeColor = new ColorRGBA(0.34f, 0.23f, 0.13f, 1f);
                hairColor = new ColorRGBA(0.34f, 0.20f, 0.10f, 1f);
            }
            case 3 -> {
                shirtColor = new ColorRGBA(0.49f, 0.29f, 0.72f, 1f);
                trouserColor = new ColorRGBA(0.22f, 0.20f, 0.40f, 1f);
                shoeColor = new ColorRGBA(0.18f, 0.16f, 0.25f, 1f);
                hairColor = new ColorRGBA(0.16f, 0.12f, 0.24f, 1f);
            }
            default -> {
                shirtColor = new ColorRGBA(0.17f, 0.47f, 0.72f, 1f);
                trouserColor = new ColorRGBA(0.20f, 0.25f, 0.37f, 1f);
                shoeColor = new ColorRGBA(0.22f, 0.16f, 0.12f, 1f);
                hairColor = new ColorRGBA(0.24f, 0.15f, 0.09f, 1f);
            }
        }
        setColor(head, skinColor);
        setColor(leftArm, skinColor);
        setColor(rightArm, skinColor);
        setColor(shirt, shirtColor);
        setColor(leftLeg, trouserColor);
        setColor(rightLeg, trouserColor);
        setColor(leftShoe, shoeColor);
        setColor(rightShoe, shoeColor);
        setColor(hair, hairColor);
    }

    private void setColor(Geometry geometry, ColorRGBA color) {
        geometry.getMaterial().setColor("Color", color);
    }

    private Geometry box(AssetManager assets, String name, float x, float y, float z,
                         float px, float py, float pz, ColorRGBA color) {
        Geometry geometry = new Geometry(name, new Box(x, y, z));
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        geometry.setMaterial(material);
        geometry.setLocalTranslation(px, py, pz);
        root.attachChild(geometry);
        return geometry;
    }

    void update(Vector3f playerEye, float yaw, boolean moving, float tpf) {
        root.setLocalTranslation(playerEye.x, playerEye.y - 1.68f, playerEye.z);
        root.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        if (moving) clock += Math.max(0f, tpf) * 8f;
        else clock *= 0.82f;
        float swing = moving ? com.jme3.math.FastMath.sin(clock) * 0.42f : 0f;
        leftArm.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X));
        rightArm.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_X));
        leftLeg.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_X));
        rightLeg.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X));
    }

    void attach(Node scene) {
        if (root.getParent() == null) scene.attachChild(root);
    }

    void detach() {
        root.removeFromParent();
    }
}