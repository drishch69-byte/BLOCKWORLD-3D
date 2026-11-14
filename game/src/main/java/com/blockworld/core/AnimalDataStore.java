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
                        Map<Long, Float> productionCooldowns,
                        Map<BlockPos, AnimalFeederRules.Stock> feeders) {
        public State(Map<Long, Float> babyAges, Map<Long, Float> cooldowns) {
            this(babyAges, cooldowns, Map.of(), Map.of());
        }
        public State(Map<Long, Float> babyAges, Map<Long, Float> cooldowns,
                     Map<Long, Float> productionCooldowns) {
            this(babyAges, cooldowns, productionCooldowns, Map.of());
        }
        public State {
            babyAges = Map.copyOf(babyAges == null ? Map.of() : babyAges);
            cooldowns = Map.copyOf(cooldowns == null ? Map.of() : cooldowns);
            productionCooldowns = Map.copyOf(productionCooldowns == null ? Map.of() : productionCooldowns);
            feeders = Map.copyOf(feeders == null ? Map.of() : feeders);
        }
        public static State empty() { return new State(Map.of(), Map.of(), Map.of(), Map.of()); }
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
        Map<BlockPos, AnimalFeederRules.Stock> feeders = new HashMap<>();
        loadValues(properties, BABY_PREFIX, AnimalBreedingRules.GROWTH_SECONDS, babyAges);
        loadValues(properties, COOLDOWN_PREFIX, AnimalBreedingRules.BREEDING_COOLDOWN_SECONDS, cooldowns);
        loadValues(properties, PRODUCTION_PREFIX, AnimalProductRules.MAX_COOLDOWN_SECONDS, productionCooldowns);
        loadFeeders(properties, feeders);
        return new State(babyAges, cooldowns, productionCooldowns, feeders);
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
        storeFeeders(properties, state.feeders());

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

    private static void loadFeeders(Properties properties,
                                    Map<BlockPos, AnimalFeederRules.Stock> destination) {
        properties.stringPropertyNames().stream().filter(key -> key.startsWith("feeder."))
                .sorted().limit(MAX_ENTRIES).forEach(key -> {
                    try {
                        String[] coordinates = key.substring("feeder.".length()).split(",");
                        String[] values = properties.getProperty(key, "").split(",");
                        if (coordinates.length != 3 || values.length != 3) return;
                        int x = Integer.parseInt(coordinates[0]);
                        int y = Integer.parseInt(coordinates[1]);
                        int z = Integer.parseInt(coordinates[2]);
                        if (Math.abs((long) x) > 100_000L || y < -512 || y > 2048
                                || Math.abs((long) z) > 100_000L) return;
                        destination.put(new BlockPos(x, y, z), new AnimalFeederRules.Stock(
                                Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                                Float.parseFloat(values[2])));
                    } catch (RuntimeException ignored) {
                        // Ignore malformed feeder entries without losing the rest of the sidecar.
                    }
                });
    }

    private static void storeFeeders(Properties properties,
                                     Map<BlockPos, AnimalFeederRules.Stock> feeders) {
        feeders.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey(java.util.Comparator
                        .comparingInt(BlockPos::x).thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::z)))
                .limit(MAX_ENTRIES).forEach(entry -> {
                    BlockPos pos = entry.getKey();
                    AnimalFeederRules.Stock stock = entry.getValue();
                    properties.setProperty("feeder." + pos.x() + "," + pos.y() + "," + pos.z(),
                            stock.wheat() + "," + stock.seeds() + "," + Float.toString(stock.secondsUntilFeed()));
                });
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
