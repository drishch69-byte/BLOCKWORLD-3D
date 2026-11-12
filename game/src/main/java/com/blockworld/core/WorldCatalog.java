package com.blockworld.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Finds and creates named worlds in the shared worlds folder. */
public final class WorldCatalog {
    private WorldCatalog() {
    }

    public static List<WorldEntry> list() throws IOException {
        Path folder = GamePaths.worldsDirectory();
        Files.createDirectories(folder);
        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bwworld"))
                    .map(WorldCatalog::toEntry)
                    .sorted(Comparator.comparing(WorldEntry::modified).reversed())
                    .toList();
        }
    }

    private static WorldEntry toEntry(Path file) {
        String filename = file.getFileName().toString();
        String name = filename.substring(0, filename.length() - ".bwworld".length());
        String version = "неизвестно";
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 12; i++) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.startsWith("gameVersion=")) {
                    version = line.substring("gameVersion=".length()).trim();
                    break;
                }
            }
        } catch (IOException ignored) {
            version = "ошибка чтения";
        }
        Instant modified = Instant.EPOCH;
        try {
            FileTime time = Files.getLastModifiedTime(file);
            modified = time.toInstant();
        } catch (IOException ignored) {
            // Keep epoch when file metadata cannot be read.
        }
        return new WorldEntry(name, file, version, modified);
    }

    public static Path pathForName(String requestedName) throws IOException {
        if (requestedName == null) {
            throw new IOException("Название мира не задано.");
        }
        String safe = requestedName.strip()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("\\.{2,}", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new IOException("Введи нормальное название мира.");
        }
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        Path folder = GamePaths.worldsDirectory();
        Files.createDirectories(folder);
        Path target = folder.resolve(safe + ".bwworld").normalize();
        if (!target.getParent().equals(folder.normalize())) {
            throw new IOException("Некорректное название мира.");
        }
        return target;
    }
}
