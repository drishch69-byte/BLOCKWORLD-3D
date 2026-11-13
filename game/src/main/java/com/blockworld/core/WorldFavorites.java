package com.blockworld.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Persistent favorites list stores only world filenames, never arbitrary paths. */
public final class WorldFavorites {
    private WorldFavorites() { }

    public static synchronized Set<String> list() throws IOException {
        Path file = GamePaths.favoritesFile();
        if (!Files.isRegularFile(file)) return Set.of();
        TreeSet<String> names = new TreeSet<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String name = line.trim();
            if (isSafeWorldName(name)) names.add(name);
        }
        return Set.copyOf(names);
    }

    public static synchronized boolean isFavorite(Path worldFile) {
        try {
            return worldFile != null && list().contains(worldFile.getFileName().toString());
        } catch (IOException error) {
            GameLog.error("Не удалось прочитать избранные миры", error);
            return false;
        }
    }

    public static synchronized void setFavorite(Path worldFile, boolean favorite) throws IOException {
        if (worldFile == null || worldFile.getFileName() == null
                || !isSafeWorldName(worldFile.getFileName().toString())) {
            throw new IOException("Некорректный файл мира.");
        }
        TreeSet<String> names = new TreeSet<>(list());
        if (favorite) names.add(worldFile.getFileName().toString());
        else names.remove(worldFile.getFileName().toString());
        Path file = GamePaths.favoritesFile();
        Files.createDirectories(file.getParent());
        if (names.isEmpty()) {
            Files.deleteIfExists(file);
        } else {
            Files.write(file, names, StandardCharsets.UTF_8);
        }
    }

    private static boolean isSafeWorldName(String name) {
        if (name == null || name.length() > 128 || !name.endsWith(".bwworld")) return false;
        String base = name.substring(0, name.length() - ".bwworld".length());
        if (base.isBlank() || base.equals(".") || base.equals("..")) return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '/' || name.indexOf(92) >= 0 || ch == ':' || ch == '*' || ch == '?'
                    || ch == '"' || ch == '<' || ch == '>' || ch == '|'
                    || Character.isISOControl(ch)) return false;
        }
        return true;
    }
}
