package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.Properties;

/** Player inventory sidecar; keeps the established world/block save format unchanged. */
public final class PlayerDataStore {
    private PlayerDataStore() { }

    public static Path sidecar(Path worldFile) {
        return worldFile.resolveSibling(worldFile.getFileName() + ".player.properties");
    }

    public static void load(Path worldFile, Map<BlockType, Integer> blocks,
                            Map<ItemType, Integer> items) throws IOException {
        load(worldFile, blocks, items, java.util.EnumSet.noneOf(ArmorSlot.class));
    }

    public static void load(Path worldFile, Map<BlockType, Integer> blocks,
                            Map<ItemType, Integer> items, Set<ArmorSlot> equippedArmor) throws IOException {
        Path file = sidecar(worldFile);
        equippedArmor.clear();
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        for (BlockType type : BlockType.values()) {
            blocks.put(type, boundedInt(properties.getProperty("block." + type.id()), blocks.getOrDefault(type, 0)));
        }
        for (ItemType type : ItemType.values()) {
            items.put(type, boundedInt(properties.getProperty("item." + type.id()), 0));
        }
        for (ArmorSlot slot : ArmorSlot.values()) {
            if (Boolean.parseBoolean(properties.getProperty("armor." + slot.id(), "false"))) {
                equippedArmor.add(slot);
            }
        }
    }

    public static void save(Path worldFile, Map<BlockType, Integer> blocks,
                            Map<ItemType, Integer> items) throws IOException {
        save(worldFile, blocks, items, java.util.EnumSet.noneOf(ArmorSlot.class));
    }

    public static void save(Path worldFile, Map<BlockType, Integer> blocks,
                            Map<ItemType, Integer> items, Set<ArmorSlot> equippedArmor) throws IOException {
        Path file = sidecar(worldFile);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties properties = new Properties();
        for (BlockType type : BlockType.values()) properties.setProperty("block." + type.id(),
                Integer.toString(Math.max(0, blocks.getOrDefault(type, 0))));
        for (ItemType type : ItemType.values()) properties.setProperty("item." + type.id(),
                Integer.toString(Math.max(0, items.getOrDefault(type, 0))));
        for (ArmorSlot slot : ArmorSlot.values()) properties.setProperty("armor." + slot.id(),
                Boolean.toString(equippedArmor.contains(slot)));
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) { properties.store(output, "CUBEVERSE 3D player inventory"); }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int boundedInt(String value, int fallback) {
        try { return Math.max(0, Math.min(1_000_000, Integer.parseInt(value))); }
        catch (RuntimeException ex) { return fallback; }
    }
}
