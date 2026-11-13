package com.blockworld.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Finds, orders and performs a lightweight header validation of saved worlds. */
public final class WorldCatalog {
    private WorldCatalog() { }

    public static List<WorldEntry> list() throws IOException {
        Path folder = GamePaths.worldsDirectory();
        Files.createDirectories(folder);
        Set<String> favorites = WorldFavorites.list();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bwworld"))
                    .map(WorldCatalog::toEntry)
                    .sorted(Comparator
                            .comparing((WorldEntry entry) -> !favorites.contains(entry.file().getFileName().toString()))
                            .thenComparing(WorldEntry::modified, Comparator.reverseOrder()))
                    .toList();
        }
    }

    private static WorldEntry toEntry(Path file) {
        String filename = file.getFileName().toString();
        String name = filename.substring(0, filename.length() - ".bwworld".length());
        String version = "неизвестно";
        String integrity = "ПОВРЕЖДЕНО";
        long size = 0L;
        try {
            size = Files.size(file);
            integrity = inspectHeader(file);
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                for (int i = 0; i < 12; i++) {
                    String line = reader.readLine();
                    if (line == null) break;
                    if (line.startsWith("gameVersion=")) {
                        version = line.substring("gameVersion=".length()).trim();
                        break;
                    }
                }
            }
        } catch (IOException error) {
            integrity = "ОШИБКА ЧТЕНИЯ";
            version = "ошибка чтения";
        }
        Instant modified = Instant.EPOCH;
        try {
            FileTime time = Files.getLastModifiedTime(file);
            modified = time.toInstant();
        } catch (IOException ignored) { }
        return new WorldEntry(name, file, version, modified, size, integrity);
    }

    /** Checks only the small metadata header; large block sections are not scanned in the launcher. */
    private static String inspectHeader(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!"BLOCKWORLD_WORLD".equals(header)) return "НЕВЕРНЫЙ ЗАГОЛОВОК";
            int format = -1;
            boolean hasBlocksMarker = false;
            String line;
            int lines = 1;
            while ((line = reader.readLine()) != null && lines++ <= 20) {
                if (line.startsWith("format=")) {
                    try { format = Integer.parseInt(line.substring(7).trim()); }
                    catch (NumberFormatException ignored) { format = -1; }
                }
                if ("--blocks--".equals(line.trim())) {
                    hasBlocksMarker = true;
                    break;
                }
            }
            if (format > VersionManager.WORLD_FORMAT) return "НУЖНА НОВАЯ ВЕРСИЯ";
            if (format < 1 || !hasBlocksMarker) return "НЕПОЛНОЕ СОХРАНЕНИЕ";
            return "ЗАГОЛОВОК В ПОРЯДКЕ";
        } catch (IOException error) {
            return "ОШИБКА ЧТЕНИЯ";
        }
    }

    /**
     * Chooses a distinct, launcher-visible file for a newly generated replacement world.
     * This prevents a failed/empty source save from being overwritten during recovery.
     */
    public static Path recoveryPathFor(Path originalWorld) throws IOException {
        if (originalWorld == null || originalWorld.getFileName() == null
                || !originalWorld.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bwworld")) {
            throw new IOException("Некорректный путь исходного мира для восстановления.");
        }
        String originalName = originalWorld.getFileName().toString();
        String stem = originalName.substring(0, originalName.length() - ".bwworld".length());
        String hint = stem.replaceAll("[^\\p{L}\\p{N}_-]+", "_");
        if (hint.length() > 10) hint = hint.substring(0, 10);
        if (hint.isBlank()) hint = "World";
        String base = "Recovered_" + hint + "_" + System.currentTimeMillis();
        Path candidate = pathForName(base);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = pathForName(base + "_" + suffix++);
        }
        return candidate;
    }

    public static Path pathForName(String requestedName) throws IOException {
        if (requestedName == null) throw new IOException("Название мира не задано.");
        String safe = requestedName.strip()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("\\.{2,}", "_")
                .replaceAll("[. ]+$", "");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new IOException("Введи нормальное название мира.");
        }
        // Windows reserves device names even when an extension is appended.
        if (safe.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\\\..*)?$")) {
            safe = "_" + safe;
        }
        if (safe.length() > 40) safe = safe.substring(0, 40);
        Path folder = GamePaths.worldsDirectory();
        Files.createDirectories(folder);
        Path target = folder.resolve(safe + ".bwworld").normalize();
        if (!target.getParent().equals(folder.normalize())) throw new IOException("Некорректное название мира.");
        return target;
    }
}
