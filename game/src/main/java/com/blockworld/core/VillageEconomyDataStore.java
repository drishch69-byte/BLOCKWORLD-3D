package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** Atomic sidecar persistence for village market stock and unfinished work cycles. */
public final class VillageEconomyDataStore {
    private VillageEconomyDataStore() { }

    public static Path sidecar(Path worldFile) {
        if (worldFile == null || worldFile.getFileName() == null) {
            throw new IllegalArgumentException("World file path is required");
        }
        return worldFile.resolveSibling(worldFile.getFileName() + ".village.properties");
    }

    public static void load(Path worldFile, VillageEconomy economy) throws IOException {
        if (economy == null) throw new IllegalArgumentException("Village economy is required");
        Path file = sidecar(worldFile);
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        EnumMap<CreatureType, Map<ItemType, Integer>> stocks = new EnumMap<>(CreatureType.class);
        EnumMap<CreatureType, Float> progress = new EnumMap<>(CreatureType.class);
        for (CreatureType profession : VillageEconomy.marketProfessions()) {
            EnumMap<ItemType, Integer> items = new EnumMap<>(ItemType.class);
            for (ItemType item : ItemType.values()) {
                String value = properties.getProperty("stock." + profession.id() + "." + item.id());
                if (value != null) items.put(item, boundedInt(value, 0, VillageEconomy.MAX_STOCK));
            }
            stocks.put(profession, items);
            String savedProgress = properties.getProperty("work." + profession.id());
            progress.put(profession, boundedFloat(savedProgress, 0f, 59.999f));
        }
        economy.restore(new VillageEconomy.Snapshot(stocks, progress));
    }

    public static void save(Path worldFile, VillageEconomy economy) throws IOException {
        if (economy == null) throw new IllegalArgumentException("Village economy is required");
        Path file = sidecar(worldFile);
        Files.createDirectories(file.toAbsolutePath().getParent());
        VillageEconomy.Snapshot snapshot = economy.snapshot();
        Properties properties = new Properties();
        for (CreatureType profession : VillageEconomy.marketProfessions()) {
            Map<ItemType, Integer> stocks = snapshot.stocks().getOrDefault(profession, Map.of());
            for (ItemType item : ItemType.values()) {
                properties.setProperty("stock." + profession.id() + "." + item.id(),
                        Integer.toString(Math.max(0, Math.min(VillageEconomy.MAX_STOCK, stocks.getOrDefault(item, 0)))));
            }
            properties.setProperty("work." + profession.id(),
                    Float.toString(snapshot.workProgress().getOrDefault(profession, 0f)));
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "BLOCKWORLD 3D village economy");
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int boundedInt(String value, int minimum, int maximum) {
        try { return Math.max(minimum, Math.min(maximum, Integer.parseInt(value))); }
        catch (RuntimeException error) { return minimum; }
    }

    private static float boundedFloat(String value, float minimum, float maximum) {
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? Math.max(minimum, Math.min(maximum, parsed)) : minimum;
        } catch (RuntimeException error) {
            return minimum;
        }
    }
}
