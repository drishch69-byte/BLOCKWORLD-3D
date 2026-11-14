package com.blockworld.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/** Writes bounded runtime diagnostics beside the project launcher log. */
public final class GameLog {
    private static final Path LOG_FILE = Path.of(".logs", "latest-run.log");
    static final long MAX_LOG_BYTES = 1024L * 1024L;

    private GameLog() { }

    public static synchronized void info(String message) {
        append(LOG_FILE, "INFO", message, null);
    }

    public static synchronized void error(String message, Throwable error) {
        append(LOG_FILE, "ERROR", message, error);
    }

    static void append(Path file, String level, String message, Throwable error) {
        StringBuilder text = new StringBuilder()
                .append('[').append(LocalDateTime.now()).append("] [")
                .append(level).append("] ").append(message).append(System.lineSeparator());
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            text.append(trace).append(System.lineSeparator());
        }
        try {
            Files.createDirectories(file.getParent());
            rotateIfNeeded(file, text.toString().getBytes(StandardCharsets.UTF_8).length);
            Files.writeString(file, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Runtime diagnostics must never crash the game.
        }
    }

    private static void rotateIfNeeded(Path file, long incomingBytes) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) + incomingBytes <= MAX_LOG_BYTES) return;
        Path previous = file.resolveSibling(file.getFileName() + ".1");
        Files.deleteIfExists(previous);
        try {
            Files.move(file, previous, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(file, previous, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
