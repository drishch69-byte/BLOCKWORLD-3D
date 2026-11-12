package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persistent video/game settings. Defaults are deliberately modest for low-end PCs. */
public record GameSettings(int width, int height, boolean fullscreen, boolean vsync,
                           int renderDistance, int fov) {
    public static final GameSettings DEFAULT = new GameSettings(1024, 640, false, false, 3, 68);

    public GameSettings {
        if (!allowedResolution(width, height)) { width = 1024; height = 640; }
        if (renderDistance != 3 && renderDistance != 4 && renderDistance != 6 && renderDistance != 8) renderDistance = 3;
        fov = Math.max(55, Math.min(100, fov));
    }

    private static boolean allowedResolution(int width, int height) {
        return (width == 1024 && height == 640) || (width == 1280 && height == 720)
                || (width == 1600 && height == 900) || (width == 1920 && height == 1080);
    }

    public static GameSettings load() {
        Path file = GamePaths.settingsFile();
        if (!Files.isRegularFile(file)) return DEFAULT;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            return new GameSettings(integer(p, "width", DEFAULT.width), integer(p, "height", DEFAULT.height),
                    Boolean.parseBoolean(p.getProperty("fullscreen", "false")),
                    Boolean.parseBoolean(p.getProperty("vsync", "false")),
                    integer(p, "renderDistance", DEFAULT.renderDistance), integer(p, "fov", DEFAULT.fov));
        } catch (IOException | RuntimeException ex) {
            GameLog.error("Не удалось прочитать настройки; использованы значения по умолчанию", ex);
            return DEFAULT;
        }
    }

    public static void save(GameSettings settings) throws IOException {
        Path file = GamePaths.settingsFile();
        Files.createDirectories(file.getParent());
        Properties p = new Properties();
        p.setProperty("width", Integer.toString(settings.width));
        p.setProperty("height", Integer.toString(settings.height));
        p.setProperty("fullscreen", Boolean.toString(settings.fullscreen));
        p.setProperty("vsync", Boolean.toString(settings.vsync));
        p.setProperty("renderDistance", Integer.toString(settings.renderDistance));
        p.setProperty("fov", Integer.toString(settings.fov));
        try (OutputStream out = Files.newOutputStream(file)) { p.store(out, "BLOCKWORLD 3D settings"); }
    }

    private static int integer(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
