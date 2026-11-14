package com.blockworld.game;

import com.blockworld.core.GameLog;
import com.blockworld.core.ItemType;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;

import java.util.EnumMap;
import java.util.Map;

/** Pixel-art player portrait and crafted-item counter list for inventory and workshop overlays. */
final class InventoryVisualPanel {
    private final Node guiNode;
    private final Node layer = new Node("InventoryVisualPanel");
    private final Picture playerSprite;
    private final BitmapText playerTitle;
    private final BitmapText itemsTitle;
    private final Map<ItemType, Picture> itemPictures = new EnumMap<>(ItemType.class);
    private final Map<ItemType, BitmapText> itemLabels = new EnumMap<>(ItemType.class);
    private boolean visible;

    InventoryVisualPanel(AssetManager assets, Node guiNode, BitmapFont font) {
        this.guiNode = guiNode;

        playerTitle = createText(font, "ПЕРСОНАЖ", 11f);
        itemsTitle = createText(font, "ПРЕДМЕТЫ", 11f);
        layer.attachChild(playerTitle);
        layer.attachChild(itemsTitle);

        playerSprite = new Picture("InventoryPlayerSprite", true);
        playerSprite.setImage(assets, "Textures/Player/player_sprite.png", true);
        playerSprite.setWidth(48f);
        playerSprite.setHeight(72f);
        playerSprite.setQueueBucket(RenderQueue.Bucket.Gui);
        layer.attachChild(playerSprite);

        for (ItemType item : ItemType.values()) {
            Picture picture = new Picture("InventoryItem-" + item.id(), true);
            try {
                picture.setImage(assets, "Textures/Items/icons/" + item.id() + ".png", true);
                picture.setWidth(18f);
                picture.setHeight(18f);
                picture.setQueueBucket(RenderQueue.Bucket.Gui);
                layer.attachChild(picture);
                itemPictures.put(item, picture);
            } catch (RuntimeException error) {
                GameLog.error("Не удалось загрузить иконку предмета " + item.id(), error);
            }

            BitmapText label = createText(font, item.displayName(), 8.5f);
            layer.attachChild(label);
            itemLabels.put(item, label);
        }
    }

    private BitmapText createText(BitmapFont font, String value, float size) {
        BitmapText text = new BitmapText(font);
        text.setText(value);
        text.setSize(size);
        text.setColor(ColorRGBA.White);
        text.setQueueBucket(RenderQueue.Bucket.Gui);
        return text;
    }

    void refresh(Map<ItemType, Integer> counts) {
        for (ItemType item : ItemType.values()) {
            int count = Math.max(0, counts.getOrDefault(item, 0));
            BitmapText label = itemLabels.get(item);
            if (label != null) {
                label.setText(item.displayName() + " ×" + count);
                label.setColor(count > 0
                        ? new ColorRGBA(0.95f, 0.92f, 0.72f, 1f)
                        : new ColorRGBA(0.62f, 0.67f, 0.72f, 1f));
            }
        }
    }

    void layout(int width, int height) {
        float centerX = width / 2f;
        float centerY = height / 2f;
        float left = centerX + 100f;

        playerTitle.setLocalTranslation(left + 70f, centerY + 239f, 12f);
        playerSprite.setPosition(left + 84f, centerY + 160f);
        playerSprite.setLocalTranslation(left + 84f, centerY + 160f, 10f);
        itemsTitle.setLocalTranslation(left, centerY + 139f, 12f);

        for (int index = 0; index < ItemType.values().length; index++) {
            ItemType item = ItemType.values()[index];
            int column = index < 16 ? 0 : 1;
            int row = index % 16;
            float x = left + column * 108f;
            float y = centerY + 108f - row * 20f;

            Picture picture = itemPictures.get(item);
            if (picture != null) {
                picture.setPosition(x, y);
                picture.setLocalTranslation(x, y, 10f);
            }

            BitmapText label = itemLabels.get(item);
            if (label != null) label.setLocalTranslation(x + 21f, y + 13f, 11f);
        }
    }

    void setVisible(boolean show) {
        if (show && !visible) {
            guiNode.attachChild(layer);
            visible = true;
        } else if (!show && visible) {
            guiNode.detachChild(layer);
            visible = false;
        }
    }
}
