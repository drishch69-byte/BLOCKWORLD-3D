package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persistent video/game settings. Defaults are deliberately modest for low-end PCs. */
public record GameSettings(int width, int height, boolean fullscreen, boolean vsync,
                           int renderDistance, int fov, int vegetationDensity, int chunkBuildsPerFrame, int fpsLimit) {
    public static final GameSettings DEFAULT = new GameSettings(1024, 640, false, false, 3, 68, 2, 2, 60);

    /** Keeps settings files and callers from older builds source-compatible. */
    public GameSettings(int width, int height, boolean fullscreen, boolean vsync,
                        int renderDistance, int fov) {
        this(width, height, fullscreen, vsync, renderDistance, fov, 2, 2, 60);
    }

    public GameSettings(int width, int height, boolean fullscreen, boolean vsync,
                        int renderDistance, int fov, int vegetationDensity, int chunkBuildsPerFrame) {
        this(width, height, fullscreen, vsync, renderDistance, fov, vegetationDensity, chunkBuildsPerFrame, 60);
    }

    public GameSettings {
        if (!allowedResolution(width, height)) { width = 1024; height = 640; }
        if (renderDistance != 3 && renderDistance != 4 && renderDistance != 6 && renderDistance != 8) renderDistance = 3;
        fov = Math.max(55, Math.min(100, fov));
        if (vegetationDensity < 0 || vegetationDensity > 3) vegetationDensity = DEFAULT.vegetationDensity;
        if (chunkBuildsPerFrame < 1 || chunkBuildsPerFrame > 6) chunkBuildsPerFrame = DEFAULT.chunkBuildsPerFrame;
        if (fpsLimit != -1 && fpsLimit != 30 && fpsLimit != 60 && fpsLimit != 90
                && fpsLimit != 120 && fpsLimit != 144) fpsLimit = DEFAULT.fpsLimit;
    }

    /**
     * Conservative graphics recommendation based on Java's configured heap and CPU cores.
     * GPU capability is unknown, so this never selects the most demanding preset by default.
     */
    public static GameSettings recommended(long maxHeapBytes, int cpuCores) {
        long heapMiB = Math.max(0L, maxHeapBytes) / (1024L * 1024L);
        int cores = Math.max(1, cpuCores);
        if (heapMiB < 3072L || cores <= 2) {
            return new GameSettings(1024, 640, false, false, 3, 68, 1, 1, 30);
        }
        if (heapMiB < 6144L || cores <= 4) {
            return new GameSettings(1280, 720, false, false, 4, 72, 2, 2, 60);
        }
        return new GameSettings(1600, 900, false, true, 6, 76, 3, 4, 90);
    }

    private static boolean allowedResolution(int width, int height) {
        return (width == 1024 && height == 640) || (width == 1280 && height == 720)
                || (width == 1600 && height == 900) || (width == 1920 && height == 1080);
    }

    public static GameSettings load() {
        return loadFrom(GamePaths.settingsFile());
    }

    /** Loads profile settings; before the first profile save, inherits legacy shared settings. */
    public static GameSettings load(String versionId) {
        Path file = GamePaths.profileSettingsFile(versionId);
        return Files.isRegularFile(file) ? loadFrom(file) : load();
    }

    private static GameSettings loadFrom(Path file) {
        if (!Files.isRegularFile(file)) return DEFAULT;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            return new GameSettings(integer(p, "width", DEFAULT.width), integer(p, "height", DEFAULT.height),
                    Boolean.parseBoolean(p.getProperty("fullscreen", "false")),
                    Boolean.parseBoolean(p.getProperty("vsync", "false")),
                    integer(p, "renderDistance", DEFAULT.renderDistance), integer(p, "fov", DEFAULT.fov),
                    integer(p, "vegetationDensity", DEFAULT.vegetationDensity),
                    integer(p, "chunkBuildsPerFrame", DEFAULT.chunkBuildsPerFrame),
                    integer(p, "fpsLimit", DEFAULT.fpsLimit));
        } catch (IOException | RuntimeException ex) {
            GameLog.error("Не удалось прочитать настройки; использованы значения по умолчанию", ex);
            return DEFAULT;
        }
    }

    /** Saves to the legacy shared settings file. */
    public static void save(GameSettings settings) throws IOException {
        saveTo(GamePaths.settingsFile(), settings);
    }

    /** Saves only to the selected version profile. */
    public static void save(String versionId, GameSettings settings) throws IOException {
        saveTo(GamePaths.profileSettingsFile(versionId), settings);
    }

    private static void saveTo(Path file, GameSettings settings) throws IOException {
        Files.createDirectories(file.getParent());
        Properties p = new Properties();
        p.setProperty("width", Integer.toString(settings.width));
        p.setProperty("height", Integer.toString(settings.height));
        p.setProperty("fullscreen", Boolean.toString(settings.fullscreen));
        p.setProperty("vsync", Boolean.toString(settings.vsync));
        p.setProperty("renderDistance", Integer.toString(settings.renderDistance));
        p.setProperty("fov", Integer.toString(settings.fov));
        p.setProperty("vegetationDensity", Integer.toString(settings.vegetationDensity));
        p.setProperty("chunkBuildsPerFrame", Integer.toString(settings.chunkBuildsPerFrame));
        p.setProperty("fpsLimit", Integer.toString(settings.fpsLimit));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                p.store(out, "Minecraft 3D profile settings");
            }
            try {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
