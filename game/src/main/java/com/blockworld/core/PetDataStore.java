package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Stores non-default companion commands without changing the established world format. */
public final class PetDataStore {
    private static final String PREFIX = "pet.";
    private static final int MAX_PETS = 256;
    private PetDataStore() { }

    public static Path sidecar(Path worldFile) {
        if (worldFile == null || worldFile.getFileName() == null)
            throw new IllegalArgumentException("Путь к сохранению мира обязателен");
        return worldFile.resolveSibling(worldFile.getFileName() + ".pets.properties");
    }

    public static void load(Path worldFile, Map<Long, PetMode> petModes) throws IOException {
        Objects.requireNonNull(petModes, "petModes");
        petModes.clear();
        Path file = sidecar(worldFile);
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        properties.stringPropertyNames().stream().filter(key -> key.startsWith(PREFIX))
                .sorted().limit(MAX_PETS).forEach(key -> {
                    try {
                        long id = Long.parseLong(key.substring(PREFIX.length()));
                        PetMode mode = PetMode.fromId(properties.getProperty(key));
                        if (id >= 0L && mode != null) petModes.put(id, mode);
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed IDs so one bad entry cannot block world loading.
                    }
                });
    }

    public static void save(Path worldFile, Map<Long, PetMode> petModes) throws IOException {
        Objects.requireNonNull(petModes, "petModes");
        Path file = sidecar(worldFile);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties properties = new Properties();
        petModes.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey() >= 0L && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey()).limit(MAX_PETS)
                .forEach(entry -> properties.setProperty(PREFIX + entry.getKey(), entry.getValue().id()));
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "CUBEVERSE 3D pet commands");
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
