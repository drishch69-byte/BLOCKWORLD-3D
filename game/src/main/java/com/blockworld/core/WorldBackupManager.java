package com.blockworld.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/** Safe backup listing, creation and restoration for a world and its progress sidecars. */
public final class WorldBackupManager {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final List<String> SIDECAR_SUFFIXES = List.of(
            ".player.properties", ".farm.properties", ".achievements.properties", ".pets.properties",
            ".animals.properties");
    private static final String MANIFEST_SUFFIX = ".manifest";
    private static final String MANIFEST_FORMAT = "1";

    private record BackupSidecars(boolean manifestPresent, Set<String> included) { }

    private WorldBackupManager() { }

    public static List<Path> list(Path worldFile) throws IOException {
        validateWorldPath(worldFile);
        Path world = worldFile.toAbsolutePath().normalize();
        Path parent = world.getParent();
        String prefix = world.getFileName() + ".backup-";
        if (!Files.isDirectory(parent)) return List.of();
        try (Stream<Path> files = Files.list(parent)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> !isAuxiliaryBackupFile(path))
                    .sorted(Comparator.comparing(WorldBackupManager::modified).reversed())
                    .toList();
        }
    }

    /** Creates a world backup plus copies of every sidecar, recording absent sidecars too. */
    public static Path create(Path worldFile) throws IOException {
        validateWorldPath(worldFile);
        Path world = worldFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(world)) throw new IOException("Сохранение мира не найдено.");
        Path parent = world.getParent();
        String base = world.getFileName() + ".backup-" + LocalDateTime.now().format(STAMP);
        Path backup = parent.resolve(base);
        int suffix = 1;
        while (Files.exists(backup)) backup = parent.resolve(base + "-" + suffix++);

        List<Path> created = new ArrayList<>();
        Path manifest = relatedFile(backup, MANIFEST_SUFFIX);
        Path manifestTemp = relatedFile(backup, MANIFEST_SUFFIX + ".tmp");
        try {
            Files.copy(world, backup, StandardCopyOption.COPY_ATTRIBUTES);
            created.add(backup);

            Properties metadata = new Properties();
            metadata.setProperty("format", MANIFEST_FORMAT);
            for (String sidecarSuffix : SIDECAR_SUFFIXES) {
                Path source = worldSidecar(world, sidecarSuffix);
                boolean exists = Files.exists(source);
                if (exists && !Files.isRegularFile(source)) {
                    throw new IOException("Файл данных мира не является обычным файлом: " + source.getFileName());
                }
                metadata.setProperty("sidecar." + sidecarSuffix.substring(1), Boolean.toString(exists));
                if (exists) {
                    Path destination = relatedFile(backup, sidecarSuffix);
                    Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    created.add(destination);
                }
            }

            try (OutputStream output = Files.newOutputStream(manifestTemp)) {
                metadata.store(output, "CUBEVERSE 3D backup sidecars");
            }
            moveReplace(manifestTemp, manifest);
            created.add(manifest);
            return backup;
        } catch (IOException | RuntimeException error) {
            for (Path createdFile : created) {
                try { Files.deleteIfExists(createdFile); }
                catch (IOException cleanupError) { error.addSuppressed(cleanupError); }
            }
            try { Files.deleteIfExists(manifestTemp); }
            catch (IOException cleanupError) { error.addSuppressed(cleanupError); }
            if (error instanceof IOException io) throw io;
            throw new IOException("Не удалось создать резервную копию мира.", error);
        }
    }

    /**
     * Restores a sibling backup. The current world and all its sidecars are copied first,
     * so a restore remains reversible even when a later file operation fails.
     */
    public static Path restore(Path worldFile, Path backupFile) throws IOException {
        validateWorldPath(worldFile);
        if (backupFile == null || backupFile.getFileName() == null) throw new IOException("Копия не выбрана.");
        Path world = worldFile.toAbsolutePath().normalize();
        Path backup = backupFile.toAbsolutePath().normalize();
        String prefix = world.getFileName() + ".backup-";
        if (backup.getParent() == null || !backup.getParent().equals(world.getParent())
                || !backup.getFileName().toString().startsWith(prefix)
                || !Files.isRegularFile(backup)) {
            throw new IOException("Выбранная копия не принадлежит этому миру.");
        }

        validateBackupHeader(backup);
        BackupSidecars sidecars = readBackupSidecars(backup);

        Path worldTemp = world.resolveSibling(world.getFileName() + ".restore.tmp");
        List<Path> stagedFiles = new ArrayList<>();
        Path safetyCopy = null;
        try {
            Files.copy(backup, worldTemp, StandardCopyOption.REPLACE_EXISTING);
            for (String sidecarSuffix : sidecars.included()) {
                Path source = relatedFile(backup, sidecarSuffix);
                Path destination = worldSidecar(world, sidecarSuffix);
                Path staged = destination.resolveSibling(destination.getFileName() + ".restore.tmp");
                Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
                stagedFiles.add(staged);
            }

            safetyCopy = create(world);
            moveReplace(worldTemp, world);

            for (String sidecarSuffix : SIDECAR_SUFFIXES) {
                Path destination = worldSidecar(world, sidecarSuffix);
                Path staged = destination.resolveSibling(destination.getFileName() + ".restore.tmp");
                if (sidecars.included().contains(sidecarSuffix)) {
                    moveReplace(staged, destination);
                } else if (sidecars.manifestPresent()) {
                    Files.deleteIfExists(destination);
                }
                // Old backups have no manifest. Their sidecar state is unknown, so keep
                // the current sidecars rather than deleting a player's newer progress.
            }
            return safetyCopy;
        } finally {
            Files.deleteIfExists(worldTemp);
            for (Path staged : stagedFiles) Files.deleteIfExists(staged);
        }
    }

    /** Deletes a backup and every companion sidecar/manifest belonging to it. */
    public static void delete(Path worldFile, Path backupFile) throws IOException {
        validateWorldPath(worldFile);
        if (backupFile == null || backupFile.getFileName() == null) throw new IOException("Копия не выбрана.");
        Path world = worldFile.toAbsolutePath().normalize();
        Path backup = backupFile.toAbsolutePath().normalize();
        String prefix = world.getFileName() + ".backup-";
        if (backup.getParent() == null || !backup.getParent().equals(world.getParent())
                || !backup.getFileName().toString().startsWith(prefix)
                || !Files.isRegularFile(backup)) {
            throw new IOException("Выбранная копия не принадлежит этому миру.");
        }
        for (String sidecarSuffix : SIDECAR_SUFFIXES) {
            Files.deleteIfExists(relatedFile(backup, sidecarSuffix));
        }
        Files.deleteIfExists(relatedFile(backup, MANIFEST_SUFFIX));
        Files.deleteIfExists(relatedFile(backup, MANIFEST_SUFFIX + ".tmp"));
        Files.delete(backup);
    }

    private static BackupSidecars readBackupSidecars(Path backup) throws IOException {
        Path manifest = relatedFile(backup, MANIFEST_SUFFIX);
        if (!Files.exists(manifest)) {
            Set<String> legacySidecars = new HashSet<>();
            for (String suffix : SIDECAR_SUFFIXES) {
                Path companion = relatedFile(backup, suffix);
                if (Files.exists(companion) && !Files.isRegularFile(companion)) {
                    throw new IOException("Повреждены дополнительные данные резервной копии.");
                }
                if (Files.isRegularFile(companion)) legacySidecars.add(suffix);
            }
            return new BackupSidecars(false, Set.copyOf(legacySidecars));
        }
        if (!Files.isRegularFile(manifest)) throw new IOException("Манифест резервной копии повреждён.");

        Properties metadata = new Properties();
        try (InputStream input = Files.newInputStream(manifest)) { metadata.load(input); }
        if (!MANIFEST_FORMAT.equals(metadata.getProperty("format"))) {
            throw new IOException("Версия манифеста резервной копии не поддерживается.");
        }

        Set<String> included = new HashSet<>();
        for (String suffix : SIDECAR_SUFFIXES) {
            String value = metadata.getProperty("sidecar." + suffix.substring(1));
            if (value == null && (".pets.properties".equals(suffix)
                    || ".animals.properties".equals(suffix))) value = "false";
            if (!"true".equals(value) && !"false".equals(value)) {
                throw new IOException("Манифест резервной копии неполный.");
            }
            Path companion = relatedFile(backup, suffix);
            if ("true".equals(value)) {
                if (!Files.isRegularFile(companion)) {
                    throw new IOException("В резервной копии отсутствует файл " + companion.getFileName());
                }
                included.add(suffix);
            } else if (Files.exists(companion)) {
                throw new IOException("В резервной копии есть неожиданный файл " + companion.getFileName());
            }
        }
        return new BackupSidecars(true, Set.copyOf(included));
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

    private static boolean isAuxiliaryBackupFile(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(MANIFEST_SUFFIX) || name.endsWith(".tmp")) return true;
        return SIDECAR_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private static Path worldSidecar(Path world, String suffix) {
        return world.resolveSibling(world.getFileName().toString() + suffix);
    }

    private static Path relatedFile(Path backup, String suffix) {
        return backup.resolveSibling(backup.getFileName().toString() + suffix);
    }

    private static void moveReplace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
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
                || !worldFile.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".bwworld")) {
            throw new IOException("Некорректный путь к миру.");
        }
    }
}
