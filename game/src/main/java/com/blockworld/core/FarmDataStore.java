package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Stores crop growth independently from the world file to retain save compatibility. */
public final class FarmDataStore {
    private static final String PREFIX = "crop.";
    private FarmDataStore() { }

    public static Path sidecar(Path worldFile) {
        return worldFile.resolveSibling(worldFile.getFileName() + ".farm.properties");
    }

    public static void load(Path worldFile, Map<BlockPos, Float> cropGrowth) throws IOException {
        cropGrowth.clear();
        Path file = sidecar(worldFile);
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX)) continue;
            String[] coordinates = key.substring(PREFIX.length()).split(",", -1);
            if (coordinates.length != 3) continue;
            try {
                BlockPos position = new BlockPos(Integer.parseInt(coordinates[0]),
                        Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2]));
                float progress = Float.parseFloat(properties.getProperty(key));
                if (Float.isFinite(progress) && progress >= 0f) {
                    cropGrowth.put(position, Math.min(FarmRules.MATURATION_SECONDS, progress));
                }
            } catch (RuntimeException ignored) {
                // A single malformed crop entry must not prevent the world from loading.
            }
        }
    }

    public static void save(Path worldFile, Map<BlockPos, Float> cropGrowth) throws IOException {
        Path file = sidecar(worldFile);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Properties properties = new Properties();
        cropGrowth.forEach((position, progress) -> {
            if (position != null && progress != null && Float.isFinite(progress) && progress >= 0f) {
                properties.setProperty(PREFIX + position.x() + "," + position.y() + "," + position.z(),
                        Float.toString(Math.min(FarmRules.MATURATION_SECONDS, progress)));
            }
        });
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) { properties.store(output, "CUBEVERSE 3D crop growth"); }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
