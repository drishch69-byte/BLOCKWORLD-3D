package com.blockworld.game;

import com.blockworld.core.CreatureType;
import com.blockworld.core.ItemType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CreatureTextureAssetsTest {
    @Test
    void everyCreatureHasAValidPixelTexture() throws IOException {
        for (CreatureType type : CreatureType.values()) {
            CreatureType textureType = AnimalBreedingRules.textureTypeFor(type);
            String path = "Textures/Creatures/" + textureType.id() + ".png";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(input, "Не найден PNG для существа: " + type.id());
                BufferedImage image = ImageIO.read(input);
                assertNotNull(image, "Повреждён PNG существа: " + type.id());
                assertEquals(16, image.getWidth(), "Ширина текстуры " + type.id());
                assertEquals(16, image.getHeight(), "Высота текстуры " + type.id());
            }
        }
    }

    @Test
    void playerSkinAndItemAtlasHaveExpectedDimensions() throws IOException {
        assertImageSize("Textures/Player/player_skin.png", 64, 64);
        assertImageSize("Textures/Player/player_sprite.png", 32, 48);
        assertImageSize("Textures/Items/items_atlas.png", 128, 64);
        for (ItemType item : ItemType.values()) {
            assertImageSize("Textures/Items/icons/" + item.id() + ".png", 16, 16);
        }
    }

    @Test
    void everyCraftedItemHasAUniqueAtlasTile() throws IOException {
        Properties mapping = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("Textures/Items/items.properties")) {
            assertNotNull(input, "Не найден каталог PNG-предметов");
            mapping.load(input);
        }

        Set<String> occupiedTiles = new HashSet<>();
        for (ItemType item : ItemType.values()) {
            String coordinate = mapping.getProperty(item.id());
            assertNotNull(coordinate, "Нет иконки предмета: " + item.id());
            String[] parts = coordinate.split(",");
            assertEquals(2, parts.length, "Неверная координата иконки: " + item.id());
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            assertTrue(x >= 0 && x < 8 && y >= 0 && y < 4,
                    "Иконка вне атласа: " + item.id());
            assertTrue(occupiedTiles.add(coordinate), "Повторяется tile: " + coordinate);
        }
    }

    private void assertImageSize(String path, int width, int height) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "Не найден ресурс: " + path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, "Повреждён PNG: " + path);
            assertEquals(width, image.getWidth());
            assertEquals(height, image.getHeight());
        }
    }
}
