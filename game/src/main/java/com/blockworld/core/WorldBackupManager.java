package com.blockworld.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Safe backup listing, creation and restoration for one named world. */
public final class WorldBackupManager {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private WorldBackupManager() { }

    public static List<Path> list(Path worldFile) throws IOException {
        validateWorldPath(worldFile);
        Path parent = worldFile.toAbsolutePath().normalize().getParent();
        String prefix = worldFile.getFileName() + ".backup-";
        if (!Files.isDirectory(parent)) return List.of();
        try (Stream<Path> files = Files.list(parent)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing(WorldBackupManager::modified).reversed())
                    .toList();
        }
    }

    public static Path create(Path worldFile) throws IOException {
        validateWorldPath(worldFile);
        if (!Files.isRegularFile(worldFile)) throw new IOException("Сохранение мира не найдено.");
        Path parent = worldFile.toAbsolutePath().normalize().getParent();
        String base = worldFile.getFileName() + ".backup-" + LocalDateTime.now().format(STAMP);
        Path backup = parent.resolve(base);
        int suffix = 1;
        while (Files.exists(backup)) backup = parent.resolve(base + "-" + suffix++);
        Files.copy(worldFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    /**
     * Restores only a sibling backup belonging to the selected world.
     * The current world is copied first, so every restore is reversible.
     */
    public static Path restore(Path worldFile, Path backupFile) throws IOException {
        validateWorldPath(worldFile);
        if (backupFile == null || backupFile.getFileName() == null) throw new IOException("Копия не выбрана.");
        Path world = worldFile.toAbsolutePath().normalize();
        Path backup = backupFile.toAbsolutePath().normalize();
        String prefix = world.getFileName() + ".backup-";
        if (!backup.getParent().equals(world.getParent())
                || !backup.getFileName().toString().startsWith(prefix)
                || !Files.isRegularFile(backup)) {
            throw new IOException("Выбранная копия не принадлежит этому миру.");
        }

        validateBackupHeader(backup);
        Path safetyCopy = create(worldFile);
        Path temp = world.resolveSibling(world.getFileName() + ".restore.tmp");
        try {
            Files.copy(backup, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, world, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temp, world, StandardCopyOption.REPLACE_EXISTING);
            }
            return safetyCopy;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void validateBackupHeader(Path backup) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(backup, StandardCharsets.UTF_8)) {
            if (!"BLOCKWORLD_WORLD".equals(reader.readLine())) {
                throw new IOException("Резервная копия имеет неверный заголовок.");
            }
            int format = -1;
            boolean hasBlocks = false;
            String line;
            int count = 1;
            while ((line = reader.readLine()) != null && count++ <= 20) {
                if (line.startsWith("format=")) {
                    try { format = Integer.parseInt(line.substring(7).trim()); }
                    catch (NumberFormatException error) { throw new IOException("Формат резервной копии не распознан.", error); }
                }
                if ("--blocks--".equals(line.trim())) {
                    hasBlocks = true;
                    break;
                }
            }
            if (format < 1 || format > VersionManager.WORLD_FORMAT || !hasBlocks) {
                throw new IOException("Резервная копия неполная или требует другой версии игры.");
            }
        }
    }

    private static Instant modified(Path file) {
        try {
            FileTime time = Files.getLastModifiedTime(file);
            return time.toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private static void validateWorldPath(Path worldFile) throws IOException {
        if (worldFile == null || worldFile.getFileName() == null || worldFile.getParent() == null
                || !worldFile.getFileName().toString().endsWith(".bwworld")) {
            throw new IOException("Некорректный путь к миру.");
        }
    }
}
