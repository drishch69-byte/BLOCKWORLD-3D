package com.blockworld.core;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** PNG texture and palette resource packs stored as folders imported from ZIP files. */
public final class ResourcePackManager {
    private static final long MAX_UNPACKED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;
    public static final int BLOCK_TEXTURE_SIZE = 16;
    public static final int ATLAS_COLUMNS = 16;
    public static final int ATLAS_SIZE = BLOCK_TEXTURE_SIZE * ATLAS_COLUMNS;
    public static final int FACE_VARIANTS_PER_BLOCK = 3;

    public static int textureTileIndex(BlockType type, int faceVariant) {
        if (type == null) throw new IllegalArgumentException("Тип блока не задан");
        if (faceVariant < 0 || faceVariant >= FACE_VARIANTS_PER_BLOCK) {
            throw new IllegalArgumentException("Вариант грани должен быть 0 (верх), 1 (бок) или 2 (низ)");
        }
        return type.textureSlot() * FACE_VARIANTS_PER_BLOCK + faceVariant;
    }
    private static final int MAX_SOURCE_TEXTURE_SIZE = 256;
    private static final long MAX_SOURCE_TEXTURE_BYTES = 2L * 1024 * 1024;
    private final Path root;

    public record ResourcePackInfo(String id, String name, String description, Path directory, boolean builtIn) {
        @Override public String toString() { return name + (builtIn ? " (встроенный)" : ""); }
    }

    public ResourcePackManager() { this(GamePaths.resourcePacksDirectory()); }
    public ResourcePackManager(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public Path root() { return root; }

    public void ensureDefaultPack() throws IOException {
        Files.createDirectories(root);
        Path dir = root.resolve("Default");
        Files.createDirectories(dir);
        Path descriptor = dir.resolve("pack.properties");
        if (!Files.exists(descriptor)) {
            Files.writeString(descriptor, "# BLOCKWORLD texture resource pack\npack.name=Default\npack.description=Стандартные пиксельные текстуры BLOCKWORLD\n");
        }
        Path defaultTextures = dir.resolve("textures").resolve("blocks");
        Files.createDirectories(defaultTextures);
        for (BlockType type : BlockType.values()) {
            Path texture = defaultTextures.resolve(type.id() + ".png");
            if (!Files.exists(texture)) writeDefaultTexture(texture, type);
        }
        Path selected = root.resolve("selected.txt");
        if (!Files.exists(selected)) Files.writeString(selected, "Default\n");
    }

    public List<ResourcePackInfo> listPacks() throws IOException {
        ensureDefaultPack();
        List<ResourcePackInfo> result = new ArrayList<>();
        try (var stream = Files.list(root)) {
            for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                Path metadata = dir.resolve("pack.properties");
                if (!Files.isRegularFile(metadata)) continue;
                Properties p = loadProperties(metadata);
                String name = p.getProperty("pack.name", dir.getFileName().toString()).trim();
                String description = p.getProperty("pack.description", "Без описания").trim();
                result.add(new ResourcePackInfo(dir.getFileName().toString(), name, description,
                        dir, dir.getFileName().toString().equals("Default")));
            }
        }
        return result;
    }

    public String selectedPackId() throws IOException {
        ensureDefaultPack();
        String value = Files.readString(root.resolve("selected.txt")).trim();
        return findPack(value) == null ? "Default" : value;
    }

    public void select(String id) throws IOException {
        if (findPack(id) == null) throw new IOException("Ресурс-пак не найден: " + id);
        Files.writeString(root.resolve("selected.txt"), id + System.lineSeparator());
    }

    public Map<BlockType, ColorRGBA> loadPalette() throws IOException { return loadPalette(selectedPackId()); }

    public Map<BlockType, ColorRGBA> loadPalette(String id) throws IOException {
        ResourcePackInfo pack = findPack(id);
        if (pack == null) pack = findPack("Default");
        Properties p = loadProperties(pack.directory().resolve("pack.properties"));
        EnumMap<BlockType, ColorRGBA> colors = new EnumMap<>(BlockType.class);
        for (BlockType block : BlockType.values()) {
            String raw = p.getProperty("block." + block.id());
            if (raw == null || raw.isBlank()) continue;
            try {
                String hex = raw.trim().replace("#", "");
                if (!hex.matches("(?i)[0-9a-f]{6}")) throw new IllegalArgumentException("ожидался #RRGGBB");
                int rgb = Integer.parseInt(hex, 16);
                colors.put(block, new ColorRGBA(((rgb >> 16) & 255) / 255f,
                        ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f));
            } catch (RuntimeException ex) {
                GameLog.error("Некорректный цвет block." + block.id() + " в паке " + id + ": " + raw, ex);
            }
        }
        return colors;
    }

    public ResourcePackInfo findPack(String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9._ -]{1,64}")) return null;
        Path dir = root.resolve(id).normalize();
        if (!dir.startsWith(root) || !Files.isRegularFile(dir.resolve("pack.properties"))) return null;
        Properties p = loadProperties(dir.resolve("pack.properties"));
        return new ResourcePackInfo(id, p.getProperty("pack.name", id),
                p.getProperty("pack.description", "Без описания"), dir, id.equals("Default"));
    }

    /** Imports a pack ZIP safely; zip-slip paths and oversized archives are rejected. */
    public ResourcePackInfo installZip(Path archive) throws IOException {
        ensureDefaultPack();
        String base = archive.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
        String id = base.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (id.isBlank()) id = "ImportedPack";
        id = id.substring(0, Math.min(64, id.length()));
        Path destination = root.resolve(id);
        int suffix = 2;
        while (Files.exists(destination)) destination = root.resolve(id + "-" + suffix++);
        Path temp = root.resolve(".import-" + UUID.randomUUID());
        Files.createDirectories(temp);
        long total = 0;
        int entries = 0;
        boolean success = false;
        try (InputStream fileIn = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("Слишком много файлов в архиве");
                Path target = temp.resolve(entry.getName()).normalize();
                if (!target.startsWith(temp)) throw new IOException("Небезопасный путь в ZIP: " + entry.getName());
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try (var out = Files.newOutputStream(target)) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        total += read;
                        if (total > MAX_UNPACKED_BYTES) throw new IOException("Распакованный пак превышает 64 МБ");
                        out.write(buffer, 0, read);
                    }
                }
            }
            if (!Files.isRegularFile(temp.resolve("pack.properties"))) {
                throw new IOException("В корне ZIP не найден pack.properties. См. resourcepacks/README.txt.");
            }
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, destination); }
            success = true;
            return findPack(destination.getFileName().toString());
        } finally {
            if (!success && Files.exists(temp)) deleteTree(temp);
        }
    }


    /**
     * Builds a nearest-filtered 16x16 atlas. Each block reserves top/side/bottom tiles.
     * Packs may provide textures/blocks/<id>.png and optional <id>_top|side|bottom.png.
     * Textures are normalized to 16x16; invalid/missing files fall back safely.
     */
    public Texture2D loadTextureAtlas() throws IOException {
        ensureDefaultPack();
        String selectedId = selectedPackId();
        ResourcePackInfo selected = findPack(selectedId);
        ResourcePackInfo defaults = findPack("Default");
        if (selected == null) selected = defaults;
        Properties properties = loadProperties(selected.directory().resolve("pack.properties"));
        EnumMap<BlockType, ColorRGBA> palette = new EnumMap<>(BlockType.class);
        for (BlockType type : BlockType.values()) {
            String raw = properties.getProperty("block." + type.id());
            if (raw == null || raw.isBlank()) continue;
            try {
                String hex = raw.trim().replace("#", "");
                if (hex.matches("(?i)[0-9a-f]{6}")) {
                    int rgb = Integer.parseInt(hex, 16);
                    palette.put(type, new ColorRGBA(((rgb >> 16) & 255) / 255f,
                            ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f));
                }
            } catch (RuntimeException ignored) {
                GameLog.info("Текстурный атлас: некорректный цвет block." + type.id() + ", используется стандартный цвет.");
            }
        }

        BufferedImage atlas = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Map<Path, BufferedImage> textureCache = new HashMap<>();
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (BlockType type : BlockType.values()) {
            for (int face = 0; face < 3; face++) {
                String suffix = face == 0 ? "_top" : face == 1 ? "_side" : "_bottom";
                Path selectedTextures = selected.directory().resolve("textures").resolve("blocks");
                BufferedImage tile = readCachedTexture(textureCache, selectedTextures.resolve(type.id() + suffix + ".png"));
                if (tile == null) tile = readCachedTexture(textureCache, selectedTextures.resolve(type.id() + ".png"));
                if (tile == null && palette.containsKey(type)) tile = solidTexture(palette.get(type));
                if (tile == null && defaults != null) {
                    Path defaultTexture = defaults.directory().resolve("textures").resolve("blocks").resolve(type.id() + ".png");
                    tile = readCachedTexture(textureCache, defaultTexture);
                }
                if (tile == null) tile = solidTexture(type.color());

                int tileIndex = textureTileIndex(type, face);
                int x = (tileIndex % ATLAS_COLUMNS) * BLOCK_TEXTURE_SIZE;
                int y = (tileIndex / ATLAS_COLUMNS) * BLOCK_TEXTURE_SIZE;
                atlasGraphics.drawImage(tile, x, y, null);
            }
        }
        atlasGraphics.dispose();

        ByteBuffer pixels = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
        // jME texture data is stored bottom row first. Flip the Java image's top-down rows.
        for (int y = ATLAS_SIZE - 1; y >= 0; y--) {
            for (int x = 0; x < ATLAS_SIZE; x++) {
                int argb = atlas.getRGB(x, y);
                pixels.put((byte) ((argb >> 16) & 255));
                pixels.put((byte) ((argb >> 8) & 255));
                pixels.put((byte) (argb & 255));
                pixels.put((byte) ((argb >>> 24) & 255));
            }
        }
        pixels.flip();
        Image image = new Image(Image.Format.RGBA8, ATLAS_SIZE, ATLAS_SIZE, pixels, ColorSpace.sRGB);
        Texture2D texture = new Texture2D(image);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        GameLog.info("Текстурный атлас собран: pack=" + selectedId + ", блоков=" + BlockType.values().length
                + ", размер=" + ATLAS_SIZE + "x" + ATLAS_SIZE);
        return texture;
    }

    private static BufferedImage readCachedTexture(Map<Path, BufferedImage> cache, Path file) {
        Path key = file.toAbsolutePath().normalize();
        if (cache.containsKey(key)) return cache.get(key);
        BufferedImage image = readOptionalTexture(key);
        cache.put(key, image);
        return image;
    }

    private static BufferedImage readOptionalTexture(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            if (Files.size(file) > MAX_SOURCE_TEXTURE_BYTES) {
                GameLog.info("Текстура пропущена (файл больше 2 МБ): " + file.getFileName());
                return null;
            }
            try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
                if (input == null) return null;
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    GameLog.info("Неизвестный PNG-формат текстуры: " + file.getFileName());
                    return null;
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                        GameLog.info("Файл текстуры пропущен: ожидался PNG — " + file.getFileName());
                        return null;
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > MAX_SOURCE_TEXTURE_SIZE || height > MAX_SOURCE_TEXTURE_SIZE) {
                        GameLog.info("Текстура пропущена (размер должен быть от 1 до 256 пикселей): " + file.getFileName());
                        return null;
                    }
                    BufferedImage source = reader.read(0);
                    if (source == null) return null;
                    BufferedImage normalized = new BufferedImage(BLOCK_TEXTURE_SIZE, BLOCK_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = normalized.createGraphics();
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    graphics.drawImage(source, 0, 0, BLOCK_TEXTURE_SIZE, BLOCK_TEXTURE_SIZE, null);
                    graphics.dispose();
                    return normalized;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException ex) {
            GameLog.error("Не удалось загрузить текстуру " + file.getFileName() + "; используется запасной вариант", ex);
            return null;
        }
    }

    private static BufferedImage solidTexture(ColorRGBA color) {
        BufferedImage image = new BufferedImage(BLOCK_TEXTURE_SIZE, BLOCK_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        int red = Math.round(color.r * 255f);
        int green = Math.round(color.g * 255f);
        int blue = Math.round(color.b * 255f);
        for (int y = 0; y < BLOCK_TEXTURE_SIZE; y++) {
            for (int x = 0; x < BLOCK_TEXTURE_SIZE; x++) {
                int variation = ((x * 13 + y * 7) % 9) - 4;
                int r = Math.max(0, Math.min(255, red + variation));
                int g = Math.max(0, Math.min(255, green + variation));
                int b = Math.max(0, Math.min(255, blue + variation));
                image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private static void writeDefaultTexture(Path file, BlockType type) throws IOException {
        Files.createDirectories(file.getParent());
        ColorRGBA base = type.color();
        BufferedImage image = new BufferedImage(BLOCK_TEXTURE_SIZE, BLOCK_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        int seed = type.ordinal() * 7919 + 31;
        Random random = new Random(seed);
        for (int y = 0; y < BLOCK_TEXTURE_SIZE; y++) {
            for (int x = 0; x < BLOCK_TEXTURE_SIZE; x++) {
                int noise = random.nextInt(31) - 15;
                int detail = 0;
                if (type == BlockType.ANCIENT_CACHE) {
                    boolean rune = (x >= 5 && x <= 10 && y >= 5 && y <= 10)
                            || (x == 7 && y >= 2 && y <= 13) || (y == 7 && x >= 2 && x <= 13);
                    boolean trim = x == 2 || x == 13 || y == 2 || y == 13;
                    int r = rune ? 40 + random.nextInt(45) : trim ? 190 + random.nextInt(45) : 112 + noise;
                    int g = rune ? 180 + random.nextInt(55) : trim ? 143 + random.nextInt(45) : 84 + noise / 2;
                    int b = rune ? 220 + random.nextInt(35) : trim ? 67 + random.nextInt(34) : 48 + noise / 2;
                    image.setRGB(x, y, 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b));
                    continue;
                }
                if (type == BlockType.CRYSTAL_CACHE) {
                    boolean trim = x == 2 || x == 13 || y == 3 || y == 12;
                    boolean gem = (x >= 6 && x <= 9 && y >= 6 && y <= 9)
                            || (x == 7 && y >= 4 && y <= 11) || (y == 7 && x >= 4 && x <= 11);
                    int r = gem ? 95 + random.nextInt(45) : trim ? 205 + random.nextInt(40)
                            : 70 + random.nextInt(35) + noise / 2;
                    int g = gem ? 205 + random.nextInt(45) : trim ? 150 + random.nextInt(55)
                            : 43 + random.nextInt(30) + noise / 2;
                    int b = gem ? 225 + random.nextInt(30) : trim ? 48 + random.nextInt(30)
                            : 105 + random.nextInt(45) + noise / 2;
                    image.setRGB(x, y, 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b));
                    continue;
                }
                if (type == BlockType.GLOWING_CRYSTAL || type == BlockType.CAVE_MOSS || type == BlockType.CAVE_FERN) {
                    int pattern = Math.floorMod(x * 11 + y * 7 + seed, 19);
                    int r;
                    int g;
                    int b;
                    if (type == BlockType.GLOWING_CRYSTAL) {
                        boolean bright = pattern < 7 || (x + y * 2) % 11 == 0;
                        r = bright ? clamp255(70 + random.nextInt(65)) : clamp255(24 + random.nextInt(24));
                        g = bright ? clamp255(210 + random.nextInt(46)) : clamp255(74 + random.nextInt(56));
                        b = bright ? clamp255(205 + random.nextInt(50)) : clamp255(90 + random.nextInt(60));
                        if (pattern == 0) { r = 235; g = 255; b = 250; }
                    } else if (type == BlockType.CAVE_MOSS) {
                        boolean moss = pattern < 10 || (x * 3 + y * 5) % 13 == 0;
                        int rock = clamp255(30 + random.nextInt(28) + noise / 3);
                        r = moss ? clamp255(22 + random.nextInt(24)) : rock;
                        g = moss ? clamp255(84 + random.nextInt(90)) : clamp255(rock + 8);
                        b = moss ? clamp255(28 + random.nextInt(30)) : clamp255(rock + 3);
                    } else {
                        boolean leaf = Math.abs(x - 8) <= (y < 8 ? y / 2 + 1 : (15 - y) / 2 + 1)
                                && (y < 8 || (x + y) % 3 != 0);
                        r = leaf ? clamp255(25 + random.nextInt(30)) : 34;
                        g = leaf ? clamp255(110 + random.nextInt(100)) : 48;
                        b = leaf ? clamp255(32 + random.nextInt(40)) : 36;
                        if (x == 7 || x == 8) { r = 78; g = 108; b = 43; }
                    }
                    image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                    continue;
                }
                if (type == BlockType.RUBY_ORE || type == BlockType.SAPPHIRE_ORE) {
                    int pattern = Math.floorMod(x * 7 + y * 11 + seed, 17);
                    boolean crystal = pattern < 5 || (x + 2 * y) % 13 == 0;
                    int r;
                    int g;
                    int b;
                    if (crystal && type == BlockType.RUBY_ORE) {
                        r = clamp255(166 + random.nextInt(72) + noise / 3);
                        g = clamp255(20 + random.nextInt(42) + noise / 4);
                        b = clamp255(42 + random.nextInt(54) + noise / 4);
                        if (pattern == 0) { r = 255; g = 144; b = 158; }
                    } else if (crystal) {
                        r = clamp255(24 + random.nextInt(42) + noise / 4);
                        g = clamp255(105 + random.nextInt(70) + noise / 3);
                        b = clamp255(170 + random.nextInt(76) + noise / 3);
                        if (pattern == 0) { r = 178; g = 229; b = 255; }
                    } else {
                        int rock = clamp255(58 + random.nextInt(34) + noise / 2);
                        r = rock;
                        g = clamp255(rock + (type == BlockType.SAPPHIRE_ORE ? 7 : 0));
                        b = clamp255(rock + (type == BlockType.SAPPHIRE_ORE ? 18 : 4));
                    }
                    image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                    continue;
                }
                if (type == BlockType.WOOD && (x % 5 == 0 || x % 5 == 1)) detail = -28;
                if (type == BlockType.STONE && (x + y * 3) % 11 == 0) detail = 24;
                if (type == BlockType.LEAVES && (x * 3 + y * 5) % 7 == 0) detail = 28;
                if (type == BlockType.WATER && (x + y) % 5 == 0) detail = 20;
                if (type == BlockType.LAVA && (x * 2 + y) % 6 == 0) detail = 34;
                int r = clamp255(Math.round(base.r * 255f) + noise + detail);
                int g = clamp255(Math.round(base.g * 255f) + noise + detail);
                int b = clamp255(Math.round(base.b * 255f) + noise + detail);
                image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private static int clamp255(int value) { return Math.max(0, Math.min(255, value)); }

    public void remove(String id) throws IOException {
        ResourcePackInfo pack = findPack(id);
        if (pack == null || pack.builtIn()) throw new IOException("Этот ресурс-пак нельзя удалить");
        if (selectedPackId().equals(id)) select("Default");
        deleteTree(pack.directory());
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(reader); }
        return p;
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
