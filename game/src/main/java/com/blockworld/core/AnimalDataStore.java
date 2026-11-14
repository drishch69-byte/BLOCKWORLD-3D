package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Persists baby growth and breeding cooldowns without changing the world save format. */
public final class AnimalDataStore {
    private static final String BABY_PREFIX = "baby.";
    private static final String COOLDOWN_PREFIX = "cooldown.";
    private static final String PRODUCTION_PREFIX = "production.";
    private static final int MAX_ENTRIES = 256;

    public record State(Map<Long, Float> babyAges, Map<Long, Float> cooldowns,
                        Map<Long, Float> productionCooldowns) {
        public State(Map<Long, Float> babyAges, Map<Long, Float> cooldowns) {
            this(babyAges, cooldowns, Map.of());
        }
        public State {
            babyAges = Map.copyOf(babyAges == null ? Map.of() : babyAges);
            cooldowns = Map.copyOf(cooldowns == null ? Map.of() : cooldowns);
            productionCooldowns = Map.copyOf(productionCooldowns == null ? Map.of() : productionCooldowns);
        }
        public static State empty() { return new State(Map.of(), Map.of(), Map.of()); }
    }

    private AnimalDataStore() { }

    public static Path sidecar(Path worldFile) {
        if (worldFile == null || worldFile.getFileName() == null) {
            throw new IllegalArgumentException("Путь к сохранению мира обязателен");
        }
        return worldFile.resolveSibling(worldFile.getFileName() + ".animals.properties");
    }

    public static State load(Path worldFile) throws IOException {
        Path file = sidecar(worldFile);
        if (!Files.isRegularFile(file)) return State.empty();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }

        Map<Long, Float> babyAges = new HashMap<>();
        Map<Long, Float> cooldowns = new HashMap<>();
        Map<Long, Float> productionCooldowns = new HashMap<>();
        loadValues(properties, BABY_PREFIX, AnimalBreedingRules.GROWTH_SECONDS, babyAges);
        loadValues(properties, COOLDOWN_PREFIX, AnimalBreedingRules.BREEDING_COOLDOWN_SECONDS, cooldowns);
        loadValues(properties, PRODUCTION_PREFIX, AnimalProductRules.MAX_COOLDOWN_SECONDS, productionCooldowns);
        return new State(babyAges, cooldowns, productionCooldowns);
    }

    private static void loadValues(Properties properties, String prefix, float maximum,
                                   Map<Long, Float> destination) {
        properties.stringPropertyNames().stream().filter(key -> key.startsWith(prefix))
                .sorted().limit(MAX_ENTRIES).forEach(key -> {
                    try {
                        long id = Long.parseLong(key.substring(prefix.length()));
                        float value = Float.parseFloat(properties.getProperty(key));
                        if (id >= 0L && Float.isFinite(value) && value >= 0f && value <= maximum) {
                            destination.put(id, value);
                        }
                    } catch (RuntimeException ignored) {
                        // Skip malformed IDs and values; a single corrupt entry must not block a world.
                    }
                });
    }

    public static void save(Path worldFile, State state) throws IOException {
        Objects.requireNonNull(state, "state");
        Path file = sidecar(worldFile);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties properties = new Properties();
        storeValues(properties, BABY_PREFIX, state.babyAges(), AnimalBreedingRules.GROWTH_SECONDS);
        storeValues(properties, COOLDOWN_PREFIX, state.cooldowns(), AnimalBreedingRules.BREEDING_COOLDOWN_SECONDS);
        storeValues(properties, PRODUCTION_PREFIX, state.productionCooldowns(),
                AnimalProductRules.MAX_COOLDOWN_SECONDS);

        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "Minecraft 3D animal growth and breeding");
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void storeValues(Properties properties, String prefix, Map<Long, Float> source,
                                    float maximum) {
        source.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey() >= 0L
                        && entry.getValue() != null && Float.isFinite(entry.getValue())
                        && entry.getValue() >= 0f && entry.getValue() <= maximum)
                .sorted(Map.Entry.comparingByKey()).limit(MAX_ENTRIES)
                .forEach(entry -> properties.setProperty(prefix + entry.getKey(),
                        Float.toString(entry.getValue())));
    }
}
