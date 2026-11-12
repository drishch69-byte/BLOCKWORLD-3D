package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Properties;

/** Atomic achievement sidecar kept outside the established world-save format. */
public final class AchievementDataStore {
    private AchievementDataStore() { }

    public static Path sidecar(Path worldFile) {
        return worldFile.resolveSibling(worldFile.getFileName() + ".achievements.properties");
    }

    public static void save(Path worldFile, AchievementTracker tracker) throws IOException {
        Path file = sidecar(worldFile);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Properties properties = new Properties();
        for (Achievement achievement : tracker.unlocked()) properties.setProperty("achievement." + achievement.id(), "true");
        for (BiomeType biome : tracker.discoveredBiomes()) properties.setProperty("biome." + biome.name(), "true");
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "BLOCKWORLD 3D achievements");
        }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void load(Path worldFile, AchievementTracker tracker) throws IOException {
        Path file = sidecar(worldFile);
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        EnumSet<Achievement> achievements = EnumSet.noneOf(Achievement.class);
        EnumSet<BiomeType> biomes = EnumSet.noneOf(BiomeType.class);
        for (String key : properties.stringPropertyNames()) {
            if (!Boolean.parseBoolean(properties.getProperty(key))) continue;
            try {
                if (key.startsWith("achievement.")) achievements.add(Achievement.fromId(key.substring("achievement.".length())));
                else if (key.startsWith("biome.")) biomes.add(BiomeType.valueOf(key.substring("biome.".length())));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown achievement and biome IDs from future versions.
            }
        }
        tracker.restore(achievements, biomes);
    }
}
