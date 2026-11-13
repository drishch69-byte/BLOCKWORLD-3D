package com.blockworld.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Persistent global player identity and lifetime play statistics. */
public final class PlayerProfileStore {
    private static final int MAX_NAME_CODE_POINTS = 24;
    private static final long MAX_COUNTER = 1_000_000_000_000L;

    private PlayerProfileStore() { }

    public record Profile(String displayName, int appearanceIndex,
                          long sessionsPlayed, long playTimeSeconds) {
        public Profile {
            displayName = cleanName(displayName);
            appearanceIndex = Math.max(0, Math.min(3, appearanceIndex));
            sessionsPlayed = Math.max(0L, Math.min(MAX_COUNTER, sessionsPlayed));
            playTimeSeconds = Math.max(0L, Math.min(MAX_COUNTER, playTimeSeconds));
        }

        public static Profile defaults() {
            return new Profile("Игрок", 0, 0L, 0L);
        }

        public Profile withIdentity(String name, int appearance) {
            return new Profile(name, appearance, sessionsPlayed, playTimeSeconds);
        }

        public Profile withAppearance(int appearance) {
            return new Profile(displayName, appearance, sessionsPlayed, playTimeSeconds);
        }

        private Profile withSessionCount(long count) {
            return new Profile(displayName, appearanceIndex, count, playTimeSeconds);
        }
    }

    public static Path profileFile() {
        return GamePaths.playerProfileFile();
    }

    public static synchronized Profile load() throws IOException {
        return load(profileFile());
    }

    public static synchronized Profile load(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) return Profile.defaults();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return new Profile(
                properties.getProperty("player.name", "Игрок"),
                parseInt(properties.getProperty("player.appearance"), 0),
                parseLong(properties.getProperty("stats.sessions"), 0L),
                parseLong(properties.getProperty("stats.playSeconds"), 0L));
    }

    public static synchronized Profile save(Profile profile) throws IOException {
        return save(profileFile(), profile);
    }

    public static synchronized Profile save(Path file, Profile profile) throws IOException {
        if (file == null) throw new IllegalArgumentException("Profile path is required");
        Profile safe = profile == null ? Profile.defaults() : profile;
        Path absolute = file.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());

        Properties properties = new Properties();
        properties.setProperty("player.name", safe.displayName());
        properties.setProperty("player.appearance", Integer.toString(safe.appearanceIndex()));
        properties.setProperty("stats.sessions", Long.toString(safe.sessionsPlayed()));
        properties.setProperty("stats.playSeconds", Long.toString(safe.playTimeSeconds()));

        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "BLOCKWORLD 3D player profile");
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
        return safe;
    }

    /** Counts a session only after a world has finished loading. */
    public static synchronized Profile recordSessionStart() throws IOException {
        Profile current = load();
        return save(current.withSessionCount(current.sessionsPlayed() + 1));
    }

    public static synchronized Profile addPlayTimeSeconds(long seconds) throws IOException {
        if (seconds <= 0) return load();
        Profile current = load();
        long updated = Math.min(MAX_COUNTER, current.playTimeSeconds()
                + Math.min(MAX_COUNTER, seconds));
        return save(new Profile(current.displayName(), current.appearanceIndex(),
                current.sessionsPlayed(), updated));
    }

    private static String cleanName(String value) {
        String cleaned = value == null ? "" : value.strip().replaceAll("[\\p{Cntrl}]", "");
        if (cleaned.isBlank()) return "Игрок";
        int count = cleaned.codePointCount(0, cleaned.length());
        if (count > MAX_NAME_CODE_POINTS) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_CODE_POINTS));
        }
        return cleaned;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException error) { return fallback; }
    }

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); }
        catch (RuntimeException error) { return fallback; }
    }
}
