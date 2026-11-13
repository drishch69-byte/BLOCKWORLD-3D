package com.blockworld.net;

import com.blockworld.core.ArmorSlot;
import com.blockworld.core.BlockType;
import com.blockworld.core.ItemType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Per-player inventory persistence for one hosted multiplayer world. */
public final class MultiplayerPlayerStore {
    private static final String HEADER = "BLOCKWORLD_PLAYER_PROFILE";
    private static final int FORMAT = 1;
    private static final int MAX_STACK = 1_000_000;
    private final Path directory;

    public MultiplayerPlayerStore(Path hostedWorldFile) {
        Path world = Objects.requireNonNull(hostedWorldFile, "hostedWorldFile")
                .toAbsolutePath().normalize();
        this.directory = world.resolveSibling(world.getFileName() + ".players");
    }

    public Path directory() { return directory; }

    public synchronized Optional<Snapshot> load(String playerName) throws IOException {
        Path file = profileFile(playerName);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) throw new IOException("Invalid player profile header");
            if (!("format=" + FORMAT).equals(reader.readLine())) throw new IOException("Unsupported player profile format");
            EnumMap<BlockType, Integer> blocks = new EnumMap<>(BlockType.class);
            EnumMap<ItemType, Integer> items = new EnumMap<>(ItemType.class);
            EnumSet<ArmorSlot> armor = EnumSet.noneOf(ArmorSlot.class);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split(" ");
                try {
                    switch (fields[0]) {
                        case "B" -> {
                            if (fields.length != 3) throw new IOException("Invalid block profile row");
                            BlockType type = BlockType.valueOf(fields[1]);
                            if (blocks.put(type, boundedStack(fields[2])) != null) throw new IOException("Duplicate block profile row");
                        }
                        case "I" -> {
                            if (fields.length != 3) throw new IOException("Invalid item profile row");
                            ItemType type = ItemType.valueOf(fields[1]);
                            if (items.put(type, boundedStack(fields[2])) != null) throw new IOException("Duplicate item profile row");
                        }
                        case "A" -> {
                            if (fields.length != 2 || !armor.add(ArmorSlot.valueOf(fields[1])))
                                throw new IOException("Invalid armor profile row");
                        }
                        default -> throw new IOException("Unknown profile row");
                    }
                } catch (IllegalArgumentException error) {
                    throw new IOException("Invalid player profile row: " + line, error);
                }
            }
            for (BlockType type : BlockType.values()) blocks.putIfAbsent(type, 0);
            for (ItemType type : ItemType.values()) items.putIfAbsent(type, 0);
            return Optional.of(new Snapshot(blocks, items, armor));
        }
    }

    public synchronized void save(String playerName, Snapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Files.createDirectories(directory);
        Path file = profileFile(playerName);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER); writer.newLine();
                writer.write("format=" + FORMAT); writer.newLine();
                for (BlockType type : BlockType.values()) {
                    writer.write("B " + type.name() + " " + snapshot.blocks().getOrDefault(type, 0));
                    writer.newLine();
                }
                for (ItemType type : ItemType.values()) {
                    writer.write("I " + type.name() + " " + snapshot.items().getOrDefault(type, 0));
                    writer.newLine();
                }
                for (ArmorSlot slot : ArmorSlot.values()) {
                    if (snapshot.armor().contains(slot)) {
                        writer.write("A " + slot.name());
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

    private Path profileFile(String playerName) {
        String clean = playerName == null ? "" : playerName.replaceAll("[^A-Za-zА-Яа-я0-9_-]", "").strip();
        if (clean.isBlank()) clean = "Player";
        clean = clean.substring(0, Math.min(20, clean.length())).toLowerCase(Locale.ROOT);
        return directory.resolve(clean + ".profile");
    }

    private static int boundedStack(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (value < 0 || value > MAX_STACK) throw new IOException("Player stack out of range");
            return value;
        } catch (NumberFormatException error) {
            throw new IOException("Invalid player stack", error);
        }
    }

    public record Snapshot(Map<BlockType, Integer> blocks, Map<ItemType, Integer> items, Set<ArmorSlot> armor) {
        public Snapshot {
            EnumMap<BlockType, Integer> blockCopy = new EnumMap<>(BlockType.class);
            EnumMap<ItemType, Integer> itemCopy = new EnumMap<>(ItemType.class);
            for (BlockType type : BlockType.values()) {
                int count = blocks == null ? 0 : blocks.getOrDefault(type, 0);
                if (count < 0 || count > MAX_STACK) throw new IllegalArgumentException("Invalid block stack");
                blockCopy.put(type, count);
            }
            for (ItemType type : ItemType.values()) {
                int count = items == null ? 0 : items.getOrDefault(type, 0);
                if (count < 0 || count > MAX_STACK) throw new IllegalArgumentException("Invalid item stack");
                itemCopy.put(type, count);
            }
            blocks = Map.copyOf(blockCopy);
            items = Map.copyOf(itemCopy);
            armor = armor == null || armor.isEmpty() ? Set.of() : Set.copyOf(armor);
        }

        public EnumMap<BlockType, Integer> mutableBlocks() { return new EnumMap<>(blocks); }
        public EnumMap<ItemType, Integer> mutableItems() { return new EnumMap<>(items); }
        public EnumSet<ArmorSlot> mutableArmor() {
            return armor.isEmpty() ? EnumSet.noneOf(ArmorSlot.class) : EnumSet.copyOf(armor);
        }
    }
}
