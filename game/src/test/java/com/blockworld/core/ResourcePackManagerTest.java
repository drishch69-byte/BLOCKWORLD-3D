package com.blockworld.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.jme3.texture.Texture2D;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePackManagerTest {
    @TempDir Path temp;

    @Test
    void importsAndReadsPalettePack() throws IOException {
        Path zip = temp.resolve("green-pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.properties"));
            out.write(("pack.name=Green\npack.description=Green blocks\nblock.grass=#55AA33\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        ResourcePackManager manager = new ResourcePackManager(temp.resolve("packs"));
        var pack = manager.installZip(zip);
        manager.select(pack.id());
        assertEquals("Green", pack.name());
        assertTrue(manager.loadPalette().containsKey(BlockType.GRASS));
    }


    @Test
    void importsPngTexturesAndBuildsTheBlockAtlas() throws IOException {
        Path zip = temp.resolve("pixel-pack.zip");
        BufferedImage grass = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < grass.getHeight(); y++) {
            for (int x = 0; x < grass.getWidth(); x++) grass.setRGB(x, y, Color.GREEN.getRGB());
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(grass, "png", png);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.properties"));
            out.write("pack.name=Pixel Pack\npack.description=PNG texture test\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("textures/blocks/grass.png"));
            out.write(png.toByteArray());
            out.closeEntry();
        }

        ResourcePackManager manager = new ResourcePackManager(temp.resolve("packs"));
        var pack = manager.installZip(zip);
        manager.select(pack.id());
        Texture2D atlas = manager.loadTextureAtlas();
        assertNotNull(atlas);
        assertNotNull(atlas.getImage());
        assertEquals(ResourcePackManager.ATLAS_SIZE, atlas.getImage().getWidth());
        assertEquals(ResourcePackManager.ATLAS_SIZE, atlas.getImage().getHeight());
        assertNotNull(atlas.getImage().getData(0));
        // The first atlas tile is the top face of grass. The imported PNG is solid green.
        var pixels = atlas.getImage().getData(0);
        int topRowFirstPixel = ((ResourcePackManager.ATLAS_SIZE - 1) * ResourcePackManager.ATLAS_SIZE) * 4;
        assertEquals(0, pixels.get(topRowFirstPixel) & 0xFF);
        assertEquals(255, pixels.get(topRowFirstPixel + 1) & 0xFF);
        assertEquals(0, pixels.get(topRowFirstPixel + 2) & 0xFF);
    }

    @Test
    void oversizedPngFallsBackInsteadOfBreakingTheAtlas() throws IOException {
        Path zip = temp.resolve("oversized-pack.zip");
        BufferedImage tooLarge = new BufferedImage(257, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(tooLarge, "png", png);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.properties"));
            out.write("pack.name=Oversized\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("textures/blocks/stone.png"));
            out.write(png.toByteArray());
            out.closeEntry();
        }
        ResourcePackManager manager = new ResourcePackManager(temp.resolve("size-check-packs"));
        var pack = manager.installZip(zip);
        manager.select(pack.id());
        assertEquals(256, manager.loadTextureAtlas().getImage().getWidth());
    }

    @Test
    void textureAtlasAllocatesStableTilesForEveryBlockFace() {
        assertEquals(0, ResourcePackManager.textureTileIndex(BlockType.GRASS, 0));
        assertEquals(1, ResourcePackManager.textureTileIndex(BlockType.GRASS, 1));
        assertEquals(2, ResourcePackManager.textureTileIndex(BlockType.GRASS, 2));
        assertEquals(30, ResourcePackManager.textureTileIndex(BlockType.LAVA, 0));
        assertEquals(32, ResourcePackManager.textureTileIndex(BlockType.LAVA, 2));
        java.util.Set<Integer> allTiles = new java.util.HashSet<>();
        for (BlockType type : BlockType.values()) {
            for (int face = 0; face < ResourcePackManager.FACE_VARIANTS_PER_BLOCK; face++) {
                assertTrue(allTiles.add(ResourcePackManager.textureTileIndex(type, face)),
                        "Повторяется слот атласа: " + type + "/" + face);
            }
        }
        assertEquals(BlockType.values().length * ResourcePackManager.FACE_VARIANTS_PER_BLOCK, allTiles.size());
        assertThrows(IllegalArgumentException.class, () -> ResourcePackManager.textureTileIndex(BlockType.GRASS, 3));
    }

    @Test
    void createsDefaultPngTexturesAndUsesFallbackForMissingFiles() throws IOException {
        ResourcePackManager manager = new ResourcePackManager(temp.resolve("default-packs"));
        Texture2D atlas = manager.loadTextureAtlas();
        assertEquals(256, atlas.getImage().getWidth());
        assertTrue(Files.isRegularFile(manager.root().resolve("Default/textures/blocks/grass.png")));
        assertTrue(Files.isRegularFile(manager.root().resolve("Default/textures/blocks/water.png")));
        Path ruby = manager.root().resolve("Default/textures/blocks/ruby_ore.png");
        Path sapphire = manager.root().resolve("Default/textures/blocks/sapphire_ore.png");
        Path glow = manager.root().resolve("Default/textures/blocks/glowing_crystal.png");
        Path moss = manager.root().resolve("Default/textures/blocks/cave_moss.png");
        Path fern = manager.root().resolve("Default/textures/blocks/cave_fern.png");
        Path cache = manager.root().resolve("Default/textures/blocks/crystal_cache.png");
        Path ancientCache = manager.root().resolve("Default/textures/blocks/ancient_cache.png");
        assertTrue(Files.isRegularFile(ruby));
        assertTrue(Files.isRegularFile(sapphire));
        assertTrue(Files.isRegularFile(glow));
        assertTrue(Files.isRegularFile(moss));
        assertTrue(Files.isRegularFile(fern));
        assertTrue(Files.isRegularFile(cache));
        assertTrue(Files.isRegularFile(ancientCache));
        assertEquals(16, ImageIO.read(ruby.toFile()).getWidth());
        assertEquals(16, ImageIO.read(sapphire.toFile()).getHeight());
        assertEquals(16, ImageIO.read(glow.toFile()).getWidth());
        assertEquals(16, ImageIO.read(moss.toFile()).getHeight());
        assertEquals(16, ImageIO.read(fern.toFile()).getWidth());
        assertEquals(16, ImageIO.read(cache.toFile()).getWidth());
        assertEquals(16, ImageIO.read(ancientCache.toFile()).getWidth());
    }

    @Test
    void rejectsZipSlipEntries() throws IOException {
        Path zip = temp.resolve("unsafe.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../escaped.txt"));
            out.write("bad".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("pack.properties"));
            out.write("pack.name=Unsafe\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        ResourcePackManager manager = new ResourcePackManager(temp.resolve("packs"));
        assertThrows(IOException.class, () -> manager.installZip(zip));
        assertFalse(Files.exists(temp.resolve("escaped.txt")));
    }

    @Test
    void allNewBlockTexturesFitInsideSixteenBySixteenAtlas() {
        assertTrue(ResourcePackManager.textureTileIndex(BlockType.EMERALD_ORE, 2)
                < ResourcePackManager.ATLAS_COLUMNS * ResourcePackManager.ATLAS_COLUMNS);
        assertEquals(16, BlockType.COPPER_ORE.textureSlot());
        assertEquals(17, BlockType.EMERALD_ORE.textureSlot());
        assertEquals(21, BlockType.TREASURE_CHEST.textureSlot());
        assertTrue(ResourcePackManager.textureTileIndex(BlockType.TREASURE_CHEST, 2)
                < ResourcePackManager.ATLAS_COLUMNS * ResourcePackManager.ATLAS_COLUMNS);
        assertEquals(18, BlockType.AMETHYST_ORE.textureSlot());
        assertEquals(19, BlockType.PODZOL.textureSlot());
        assertEquals(20, BlockType.MUD.textureSlot());
        assertEquals(22, BlockType.RUBY_ORE.textureSlot());
        assertEquals(23, BlockType.SAPPHIRE_ORE.textureSlot());
        assertEquals(24, BlockType.GLOWING_CRYSTAL.textureSlot());
        assertEquals(25, BlockType.CAVE_MOSS.textureSlot());
        assertEquals(26, BlockType.CAVE_FERN.textureSlot());
        assertEquals(27, BlockType.CRYSTAL_CACHE.textureSlot());
        assertEquals(28, BlockType.ANCIENT_CACHE.textureSlot());
        assertTrue(ResourcePackManager.textureTileIndex(BlockType.CAVE_FERN, 2)
                < ResourcePackManager.ATLAS_COLUMNS * ResourcePackManager.ATLAS_COLUMNS);
        assertTrue(ResourcePackManager.textureTileIndex(BlockType.CRYSTAL_CACHE, 2)
                < ResourcePackManager.ATLAS_COLUMNS * ResourcePackManager.ATLAS_COLUMNS);
        assertTrue(ResourcePackManager.textureTileIndex(BlockType.ANCIENT_CACHE, 2)
                < ResourcePackManager.ATLAS_COLUMNS * ResourcePackManager.ATLAS_COLUMNS);
        assertTrue(ResourcePackManager.textureTileIndex(BlockType.SAPPHIRE_ORE, 2)
                < ResourcePackManager.ATLAS_COLUMNS * ResourcePackManager.ATLAS_COLUMNS);
        assertTrue(ResourcePackManager.textureTileIndex(BlockType.MUD, 2)
                < ResourcePackManager.ATLAS_COLUMNS * ResourcePackManager.ATLAS_COLUMNS);
    }
}
