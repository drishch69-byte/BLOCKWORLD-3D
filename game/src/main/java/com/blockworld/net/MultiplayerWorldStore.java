package com.blockworld.net;

import com.blockworld.core.BlockPos;
import com.blockworld.core.BlockType;
import com.blockworld.core.CreatureType;
import com.blockworld.core.WorldCreature;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class MultiplayerWorldStore {
    private static final String HEADER = "BLOCKWORLD_SERVER_WORLD";
    private static final int FORMAT = 1;
    private static final int MAX_BLOCKS = 1_000_000;
    private static final int MAX_CREATURES = 256;
    private final Path file;

    public MultiplayerWorldStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public Path file() { return file; }

    public synchronized Optional<Snapshot> load() throws IOException {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) throw new IOException("Invalid multiplayer world header");
            if (!("format=" + FORMAT).equals(reader.readLine())) throw new IOException("Unsupported multiplayer world format");
            long seed = parseLong(reader.readLine(), "seed=");
            boolean active = parseBoolean(reader.readLine(), "activeBiomes=");
            boolean extended = parseBoolean(reader.readLine(), "extendedBiomes=");
            int radius = parseInt(reader.readLine(), "radius=", 0, 100_000);
            if (!"--blocks--".equals(reader.readLine())) throw new IOException("Missing blocks section");
            Map<BlockPos, BlockType> blocks = new HashMap<>();
            List<WorldCreature> creatures = new ArrayList<>();
            boolean inCreatures = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if ("--creatures--".equals(line)) { inCreatures = true; continue; }
                String[] fields = line.split(" ");
                try {
                    if (!inCreatures) {
                        if (fields.length != 5 || !"B".equals(fields[0]) || blocks.size() >= MAX_BLOCKS)
                            throw new IOException("Invalid or oversized block section");
                        BlockPos pos = new BlockPos(bounded(fields[1], -100_000, 100_000),
                                bounded(fields[2], -512, 2048), bounded(fields[3], -100_000, 100_000));
                        BlockType type = BlockType.valueOf(fields[4]);
                        if (blocks.put(pos, type) != null) throw new IOException("Duplicate block position");
                    } else {
                        if (fields.length != 7 || !"C".equals(fields[0]) || creatures.size() >= MAX_CREATURES)
                            throw new IOException("Invalid or oversized creature section");
                        creatures.add(new WorldCreature(Long.parseLong(fields[1]), CreatureType.valueOf(fields[2]),
                                Float.parseFloat(fields[3]), Float.parseFloat(fields[4]),
                                Float.parseFloat(fields[5]), Float.parseFloat(fields[6])));
                    }
                } catch (IllegalArgumentException error) {
                    throw new IOException("Invalid multiplayer world row: " + line, error);
                }
            }
            return Optional.of(new Snapshot(seed, active, extended, radius, blocks, creatures));
        }
    }

    public synchronized void save(long seed, boolean activeBiomes, boolean extendedBiomes, int radius,
                                  Map<BlockPos, BlockType> blocks, List<WorldCreature> creatures) throws IOException {
        if (blocks == null || blocks.size() > MAX_BLOCKS) throw new IOException("World block limit exceeded");
        if (creatures == null || creatures.size() > MAX_CREATURES) throw new IOException("World creature limit exceeded");
        if (radius < 0 || radius > 100_000) throw new IOException("World radius out of range");
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER); writer.newLine();
                writer.write("format=" + FORMAT); writer.newLine();
                writer.write("seed=" + seed); writer.newLine();
                writer.write("activeBiomes=" + activeBiomes); writer.newLine();
                writer.write("extendedBiomes=" + extendedBiomes); writer.newLine();
                writer.write("radius=" + radius); writer.newLine();
                writer.write("--blocks--"); writer.newLine();
                for (Map.Entry<BlockPos, BlockType> entry : blocks.entrySet()) {
                    BlockPos p = entry.getKey();
                    writer.write("B " + p.x() + " " + p.y() + " " + p.z() + " " + entry.getValue().name());
                    writer.newLine();
                }
                writer.write("--creatures--"); writer.newLine();
                for (WorldCreature creature : creatures) {
                    writer.write("C " + creature.id() + " " + creature.type().name() + " "
                            + creature.x() + " " + creature.y() + " " + creature.z() + " " + creature.yaw());
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static long parseLong(String line, String prefix) throws IOException {
        if (line == null || !line.startsWith(prefix)) throw new IOException("Missing field " + prefix);
        try { return Long.parseLong(line.substring(prefix.length())); }
        catch (NumberFormatException error) { throw new IOException("Invalid field " + prefix, error); }
    }
    private static int parseInt(String line, String prefix, int min, int max) throws IOException {
        if (line == null || !line.startsWith(prefix)) throw new IOException("Missing field " + prefix);
        return bounded(line.substring(prefix.length()), min, max);
    }
    private static boolean parseBoolean(String line, String prefix) throws IOException {
        if (line == null || !line.startsWith(prefix)) throw new IOException("Missing field " + prefix);
        String value = line.substring(prefix.length());
        if (!value.equals("true") && !value.equals("false")) throw new IOException("Invalid field " + prefix);
        return Boolean.parseBoolean(value);
    }
    private static int bounded(String text, int min, int max) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) throw new IOException("Coordinate out of range");
            return value;
        } catch (NumberFormatException error) { throw new IOException("Invalid integer", error); }
    }

    public record Snapshot(long seed, boolean activeBiomes, boolean extendedBiomes, int radius,
                           Map<BlockPos, BlockType> blocks, List<WorldCreature> creatures) {
        public Snapshot { blocks = Map.copyOf(blocks); creatures = List.copyOf(creatures); }
    }
}
