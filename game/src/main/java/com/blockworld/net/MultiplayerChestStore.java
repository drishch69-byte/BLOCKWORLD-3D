package com.blockworld.net;

import com.blockworld.core.BlockPos;
import com.blockworld.core.ItemType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent shared chest contents for one hosted world. */
public final class MultiplayerChestStore {
    private static final String HEADER = "BLOCKWORLD_SHARED_CHESTS";
    private static final int FORMAT = 1;
    private static final int MAX_CHESTS = 100_000;
    private static final int MAX_STACK = 1_000_000;
    private final Path file;

    public MultiplayerChestStore(Path hostedWorldFile) {
        Path world = Objects.requireNonNull(hostedWorldFile, "hostedWorldFile").toAbsolutePath().normalize();
        file = world.resolveSibling(world.getFileName() + ".chests");
    }

    public synchronized Map<BlockPos, Map<ItemType, Integer>> load() throws IOException {
        Map<BlockPos, Map<ItemType, Integer>> result = new HashMap<>();
        if (!Files.isRegularFile(file)) return result;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine()) || !("format=" + FORMAT).equals(reader.readLine()))
                throw new IOException("Invalid shared chest file");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(" ");
                try {
                    if (p.length == 4 && "C".equals(p[0])) {
                        BlockPos pos = new BlockPos(bounded(p[1], -100_000, 100_000),
                                bounded(p[2], -512, 2048), bounded(p[3], -100_000, 100_000));
                        result.computeIfAbsent(pos, ignored -> new EnumMap<>(ItemType.class));
                    } else if (p.length == 6 && "I".equals(p[0])) {
                        BlockPos pos = new BlockPos(bounded(p[1], -100_000, 100_000),
                                bounded(p[2], -512, 2048), bounded(p[3], -100_000, 100_000));
                        ItemType item = ItemType.valueOf(p[4]);
                        int count = bounded(p[5], 1, MAX_STACK);
                        Map<ItemType, Integer> contents = result.computeIfAbsent(pos, ignored -> new EnumMap<>(ItemType.class));
                        if (contents.put(item, count) != null) throw new IOException("Duplicate shared chest item");
                    } else {
                        throw new IOException("Invalid shared chest row");
                    }
                    if (result.size() > MAX_CHESTS) throw new IOException("Too many shared chests");
                } catch (IllegalArgumentException error) {
                    throw new IOException("Invalid shared chest row: " + line, error);
                }
            }
        }
        return deepCopy(result);
    }

    public synchronized void save(Map<BlockPos, Map<ItemType, Integer>> containers) throws IOException {
        if (containers == null || containers.size() > MAX_CHESTS)
            throw new IOException("Shared chest limit exceeded");
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER); writer.newLine();
                writer.write("format=" + FORMAT); writer.newLine();
                for (Map.Entry<BlockPos, Map<ItemType, Integer>> chest : containers.entrySet()) {
                    BlockPos p = chest.getKey();
                    writer.write("C " + p.x() + " " + p.y() + " " + p.z());
                    writer.newLine();
                    for (Map.Entry<ItemType, Integer> item : chest.getValue().entrySet()) {
                        int count = item.getValue() == null ? 0 : item.getValue();
                        if (count < 1 || count > MAX_STACK) continue;
                        writer.write("I " + p.x() + " " + p.y() + " " + p.z() + " "
                                + item.getKey().name() + " " + count);
                        writer.newLine();
                    }
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

    public static Map<BlockPos, Map<ItemType, Integer>> deepCopy(
            Map<BlockPos, Map<ItemType, Integer>> source) {
        Map<BlockPos, Map<ItemType, Integer>> copy = new HashMap<>();
        if (source != null) source.forEach((pos, items) -> {
            EnumMap<ItemType, Integer> contents = new EnumMap<>(ItemType.class);
            if (items != null) items.forEach((type, count) -> {
                if (type != null && count != null && count > 0 && count <= MAX_STACK) contents.put(type, count);
            });
            copy.put(pos, contents);
        });
        return copy;
    }

    private static int bounded(String text, int min, int max) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) throw new IOException("Shared chest value out of range");
            return value;
        } catch (NumberFormatException error) {
            throw new IOException("Invalid shared chest integer", error);
        }
    }
}
